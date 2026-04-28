package io.github.movebrickschi.dailynewspapergenerator.llm;

/**
 * 大模型调用异常。
 *
 * @author Liu Chunchi
 */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
