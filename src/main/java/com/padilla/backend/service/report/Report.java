package com.padilla.backend.service.report;

import java.util.List;
import java.util.Map;

/**
 * PRO-072: resultado de un reporte, independiente del formato (JSON, Excel o PDF).
 * columns: clave de cada valor en rows + titulo visible.
 */
public record Report(String title, String subtitle, List<Column> columns, List<Map<String, Object>> rows) {

    public record Column(String key, String label) {}
}
