package cn.think.in.java.entity;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * 附加 RPC 日志返回值.
 *
 * @author 莫那·鲁道
 */
@Setter
@Getter
@ToString
@Builder
public class AentryResult implements Serializable {

    /** 当前的任期号，用于领导人去更新自己 */
    long term;

    /** 跟随者包含了匹配上 prevLogIndex 和 prevLogTerm 的日志时为真  */
    boolean success;
    
    boolean notChangeIndex;

    public AentryResult(long term) {
        this.term = term;
    }

    public AentryResult(boolean success) {
        this.success = success;
    }

    public AentryResult(long term, boolean success) {
        this.term = term;
        this.success = success;
    } 
    
    public AentryResult(long term, boolean success,boolean notChangeIndex) {
        this.term = term;
        this.success = success;
        this.notChangeIndex = notChangeIndex;
    } 

    public static AentryResult fail() {
        return new AentryResult(false);
    }

    public static AentryResult ok() {
        return new AentryResult(true);
    } 
}
