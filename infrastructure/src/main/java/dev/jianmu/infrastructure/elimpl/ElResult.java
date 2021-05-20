package dev.jianmu.infrastructure.elimpl;


import dev.jianmu.workflow.el.EvaluationResult;
import dev.jianmu.workflow.el.ResultType;

/**
 * @class: ElResult
 * @description: 表达式计算结果
 * @author: Ethan Liu
 * @create: 2021-03-08 22:02
 **/
public class ElResult implements EvaluationResult {

    private String expr;
    private Object result;

    public ElResult(String expr, Object result) {
        this.expr = expr;
        this.result = result;
    }

    @Override
    public String getExpression() {
        return this.expr;
    }

    @Override
    public boolean isFailure() {
        return false;
    }

    @Override
    public String getFailureMessage() {
        return "";
    }

    @Override
    public ResultType getType() {
        if (this.result instanceof String) {
            return ResultType.STRING;
        }
        if (this.result instanceof Boolean) {
            return ResultType.BOOLEAN;
        }
        throw new ClassCastException("不支持的类型");
    }

    @Override
    public String getString() {
        return (String) this.result;
    }

    @Override
    public Boolean getBoolean() {
        return (Boolean) this.result;
    }
}
