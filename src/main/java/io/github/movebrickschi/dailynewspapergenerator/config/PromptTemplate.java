package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.util.xmlb.annotations.Tag;

import java.util.Objects;
import java.util.UUID;

/**
 * 一条提示词模板。
 *
 * @author Liu Chunchi
 */
@Tag("template")
public class PromptTemplate {

    /** 唯一标识，用于在 {@link LlmSettings#activeTemplateId} 中引用。 */
    public String id = UUID.randomUUID().toString();

    /** 展示名，例如「日报」「周报」「英文」。 */
    public String name = "";

    /** 模板正文，会作为 system prompt 传给大模型。 */
    public String content = "";

    public PromptTemplate() {
    }

    public PromptTemplate(String name, String content) {
        this.name = name == null ? "" : name;
        this.content = content == null ? "" : content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PromptTemplate that = (PromptTemplate) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, content);
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? "(未命名模板)" : name;
    }
}
