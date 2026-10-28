package com.padilla.backend.service.contract;

import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.ContractPeriod;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.repository.ContractPeriodRepository;
import com.padilla.backend.repository.ContractRepository;
import com.padilla.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PRO-045: nuevos periodos de un contrato (ajustes por indice, ICL).
 * - Se indica el monto nuevo, o un coeficiente (adjustmentIndex, ej. 1.1523) que se aplica al alquiler
 *   del periodo anterior.
 * - Comision y gastos administrativos se recalculan con los % del contrato.
 * - Si el periodo nuevo ya esta vigente (desde <= hoy), actualiza el monto mensual del contrato.
 */
@Service
@RequiredArgsConstructor
public class ContractPeriodService {

    private final ContractRepository contractRepository;
    private final ContractPeriodRepository periodRepository;
    private final Clock clock;

    @Transactional
    public ContractPeriod addPeriod(UUID contractId, LocalDate periodFrom, BigDecimal rentAmount, BigDecimal adjustmentIndex) {
        CurrentUser.requireManagement();
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("Contrato no encontrado: " + contractId));
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ConflictException("Solo se agregan periodos a contratos vigentes");
        }
        if (periodFrom == null) {
            throw new BadRequestException("La fecha de inicio del periodo es obligatoria");
        }
        if (periodFrom.isBefore(contract.getStartDate()) || periodFrom.isAfter(contract.getEndDate())) {
            throw new BadRequestException("El periodo tiene que empezar dentro de la vigencia del contrato ("
                    + contract.getStartDate() + " a " + contract.getEndDate() + ")");
        }
        List<ContractPeriod> periods = periodRepository.findByContractIdOrderByPeriodFromAsc(contractId);
        ContractPeriod last = periods.isEmpty() ? null : periods.get(periods.size() - 1);
        if (last != null && !periodFrom.isAfter(last.getPeriodFrom())) {
            throw new ConflictException("El periodo tiene que empezar despues del ultimo (" + last.getPeriodFrom() + ")");
        }

        BigDecimal rent;
        if (rentAmount != null) {
            rent = rentAmount;
        } else if (adjustmentIndex != null && last != null) {
            rent = last.getRentAmount().multiply(adjustmentIndex).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BadRequestException("Indica el monto nuevo o el coeficiente de ajuste");
        }
        if (rent.signum() <= 0) {
            throw new BadRequestException("El monto del alquiler tiene que ser mayor a cero");
        }

        ContractPeriod p = new ContractPeriod();
        p.setContractId(contractId);
        p.setPeriodFrom(periodFrom);
        p.setRentAmount(rent);
        p.setCommissionAmount(pct(rent, contract.getCommissionPct()));
        p.setAdminFeeAmount(pct(rent, contract.getAdminFeePct()));
        p.setAdjustmentIndex(adjustmentIndex != null ? adjustmentIndex : BigDecimal.ZERO);
        ContractPeriod saved = periodRepository.save(p);

        if (!periodFrom.isAfter(LocalDate.now(clock))) {
            contract.setMonthlyAmount(rent);
            contractRepository.save(contract);
        }
        return saved;
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal pct) {
        return pct == null ? null : base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
