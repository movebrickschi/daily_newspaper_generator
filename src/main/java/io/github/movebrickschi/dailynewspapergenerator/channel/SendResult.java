package io.github.movebrickschi.dailynewspapergenerator.channel;

/**
 * 推送结果。
 *
 * @author Liu Chunchi
 */
public record SendResult(boolean success, String message) {

    public static SendResult ok() {
        return new SendResult(true, "");
    }

    public static SendResult ok(String message) {
        return new SendResult(true, message);
    }

    public static SendResult failure(String message) {
        return new SendResult(false, message);
    }
}
