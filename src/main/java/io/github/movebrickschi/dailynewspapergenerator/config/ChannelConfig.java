package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.util.xmlb.annotations.Tag;

import java.util.Objects;
import java.util.UUID;

/**
 * 一个推送通道的配置。
 * 不同 {@link ChannelType} 使用其中部分字段，未用字段保持空。
 *
 * @author Liu Chunchi
 */
@Tag("channel")
public class ChannelConfig {

    public enum ChannelType {
        /** 飞书自定义机器人 webhook。 */
        FEISHU_ROBOT,
        /** 钉钉自定义机器人 webhook。 */
        DINGTALK_ROBOT,
        /** 钉钉企业内部应用日志 / report 模板（OAPI）。 */
        DINGTALK_REPORT,
        /** 企业微信群机器人 webhook。 */
        WECOM_ROBOT,
        /** 通用 webhook，可自定义请求体模板。 */
        GENERIC_WEBHOOK
    }

    /** 唯一 id（用于 PasswordSafe 凭证 key 拼接）。 */
    public String id = UUID.randomUUID().toString();

    /** 用户给通道起的名字（仅 UI 展示用）。 */
    public String name = "";

    public ChannelType type = ChannelType.FEISHU_ROBOT;

    // ===== 机器人通道（飞书/钉钉群/企微/通用） =====

    /** Webhook URL（机器人通道使用）。 */
    public String webhookUrl = "";

    /** 通用 webhook 的 JSON 请求体模板（支持 {{title}} {{content}}）。 */
    public String genericTemplate = "{\"title\":\"{{title}}\",\"content\":\"{{content}}\"}";

    /** 标题，主要用于卡片类（飞书 interactive）。 */
    public String title = "日报";

    // ===== 钉钉日志通道 =====

    /** 自建应用 AppKey（钉钉日志）。 */
    public String dingAppKey = "";

    /** 模板 ID（钉钉日志）。 */
    public String dingTemplateId = "";

    /** 钉钉日志主字段名（必须与后台模板字段名完全一致）。 */
    public String dingMainField = "今日工作";

    /** 接收人 userid 列表，逗号分隔。 */
    public String dingToUserIds = "";

    /** 是否同步到群。 */
    public boolean dingToChat = false;

    public ChannelConfig() {
    }

    public ChannelConfig(ChannelType type, String name) {
        this.type = type == null ? ChannelType.FEISHU_ROBOT : type;
        this.name = name == null ? "" : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelConfig that = (ChannelConfig) o;
        return dingToChat == that.dingToChat
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && type == that.type
                && Objects.equals(webhookUrl, that.webhookUrl)
                && Objects.equals(genericTemplate, that.genericTemplate)
                && Objects.equals(title, that.title)
                && Objects.equals(dingAppKey, that.dingAppKey)
                && Objects.equals(dingTemplateId, that.dingTemplateId)
                && Objects.equals(dingMainField, that.dingMainField)
                && Objects.equals(dingToUserIds, that.dingToUserIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, webhookUrl, genericTemplate, title,
                dingAppKey, dingTemplateId, dingMainField, dingToUserIds, dingToChat);
    }

    @Override
    public String toString() {
        String n = name == null || name.isBlank() ? "(未命名)" : name;
        return n + "[" + (type == null ? "?" : type.name()) + "]";
    }

    /** 通道凭证在 PasswordSafe 中的 key（webhook secret / sign / AppSecret 等）。 */
    public String secretKey() {
        return "channel." + id + ".secret";
    }
}
