package VO;

import lombok.Data;
import java.io.Serializable;

@Data
public class RpcResponse implements Serializable {
    private Object data;      // 返回结果
    private String message;   // 状态信息
}