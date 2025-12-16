package VO;

import lombok.Data;
import java.io.Serializable;

@Data
public class RpcRequest implements Serializable {
    private String interfaceName; // 调用的接口名
    private String methodName;    // 调用的方法名
    private Class<?>[] paramTypes;// 参数类型
    private Object[] parameters;  // 参数值
}