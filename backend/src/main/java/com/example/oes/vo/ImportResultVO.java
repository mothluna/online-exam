package com.example.oes.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportResultVO {

    /** 文件中的数据行数（不含表头与空行） */
    private int total;

    private int success;

    private int failed;

    /** 服务端处理耗时（毫秒），不含网络上传 */
    private long elapsedMs;

    /** 失败原因预览，最多 20 条 */
    private List<String> errors;

    /** 失败明细文件下载地址（预签名，15 分钟有效）；无失败时为 null */
    private String errorFileUrl;

    private String errorFileKey;
}
