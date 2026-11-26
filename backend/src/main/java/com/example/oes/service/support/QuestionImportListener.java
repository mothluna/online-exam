package com.example.oes.service.support;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.example.oes.dto.QuestionImportRow;

/**
 * 流式读取监听器。不缓存全表数据，按批触发落库，
 * 内存占用与文件行数无关。
 */
public class QuestionImportListener extends AnalysisEventListener<QuestionImportRow> {

    private final RowHandler handler;
    private final int batchSize;
    private int pending = 0;

    public QuestionImportListener(RowHandler handler, int batchSize) {
        this.handler = handler;
        this.batchSize = batchSize;
    }

    @Override
    public void invoke(QuestionImportRow row, AnalysisContext context) {
        // getRowIndex 从 0 开始且包含表头，+1 后与 Excel 里看到的行号一致
        int rowNo = context.readRowHolder().getRowIndex() + 1;
        handler.handle(rowNo, row);
        if (++pending >= batchSize) {
            handler.flush();
            pending = 0;
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        handler.flush();
        pending = 0;
    }

    public interface RowHandler {
        void handle(int rowNo, QuestionImportRow row);

        void flush();
    }
}
