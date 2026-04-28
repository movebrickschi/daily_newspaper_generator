package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.movebrickschi.dailynewspapergenerator.config.ChannelConfig.ChannelType;
import io.github.movebrickschi.dailynewspapergenerator.interfaces.LlmSettingsComponent;
import io.github.movebrickschi.dailynewspapergenerator.ui.ChannelListPanel;
import io.github.movebrickschi.dailynewspapergenerator.ui.PromptTemplateListPanel;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.List;

public class LlmSettingsConfigurableTest extends BasePlatformTestCase {

    public void testResetRestoresPromptTemplatesAndChannels() throws Exception {
        PromptTemplate savedTemplate = new PromptTemplate("Saved Daily", "saved prompt");
        ChannelConfig savedChannel = new ChannelConfig(ChannelType.FEISHU_ROBOT, "Saved Feishu");
        savedChannel.webhookUrl = "https://example.test/saved";

        LlmSettings settings = LlmSettings.getInstance();
        settings.templates = List.of(savedTemplate);
        settings.activeTemplateId = savedTemplate.id;
        settings.channels = List.of(savedChannel);

        LlmSettingsConfigurable configurable = new LlmSettingsConfigurable();
        configurable.createComponent();
        LlmSettingsComponent component = field(configurable, "mySettingsComponent");

        PromptTemplate editedTemplate = new PromptTemplate("Edited Weekly", "edited prompt");
        ChannelConfig editedChannel = new ChannelConfig(ChannelType.GENERIC_WEBHOOK, "Edited Webhook");
        editedChannel.webhookUrl = "https://example.test/edited";

        PromptTemplateListPanel templatePanel = field(component, "myTemplateListPanel");
        ChannelListPanel channelPanel = field(component, "myChannelListPanel");
        templatePanel.setData(List.of(editedTemplate), editedTemplate.id);
        channelPanel.setData(List.of(editedChannel));

        assertTrue(configurable.isModified());

        configurable.reset();

        assertEquals(List.of(savedTemplate), component.getTemplates());
        assertEquals(savedTemplate.id, component.getActiveTemplateId());
        assertEquals(List.of(savedChannel), component.getChannels());
    }

    public void testEditingChannelSecretDoesNotPersistBeforeApply() throws Exception {
        ChannelConfig channel = new ChannelConfig(ChannelType.FEISHU_ROBOT, "Feishu");
        SecureKeyStore.store(channel.secretKey(), null);

        ChannelListPanel panel = new ChannelListPanel();
        panel.setData(List.of(channel));

        JPasswordField secretField = field(panel, "secretField");
        secretField.setText("draft-secret");

        assertEquals("", SecureKeyStore.load(channel.secretKey()));
    }

    public void testEditingOnlyChannelSecretMarksSettingsModified() throws Exception {
        ChannelConfig channel = new ChannelConfig(ChannelType.FEISHU_ROBOT, "Feishu");
        SecureKeyStore.store(channel.secretKey(), "saved-secret");
        try {
            LlmSettings settings = LlmSettings.getInstance();
            settings.channels = List.of(channel);

            LlmSettingsConfigurable configurable = new LlmSettingsConfigurable();
            configurable.createComponent();
            LlmSettingsComponent component = field(configurable, "mySettingsComponent");
            ChannelListPanel channelPanel = field(component, "myChannelListPanel");

            JPasswordField secretField = field(channelPanel, "secretField");
            secretField.setText("changed-secret");

            assertTrue(configurable.isModified());
        } finally {
            SecureKeyStore.store(channel.secretKey(), null);
        }
    }

    public void testApplyPersistsChannelSecretDraft() throws Exception {
        ChannelConfig channel = new ChannelConfig(ChannelType.FEISHU_ROBOT, "Feishu");
        SecureKeyStore.store(channel.secretKey(), null);
        try {
            LlmSettings settings = LlmSettings.getInstance();
            settings.channels = List.of(channel);

            LlmSettingsConfigurable configurable = new LlmSettingsConfigurable();
            configurable.createComponent();
            LlmSettingsComponent component = field(configurable, "mySettingsComponent");
            ChannelListPanel channelPanel = field(component, "myChannelListPanel");

            JPasswordField secretField = field(channelPanel, "secretField");
            secretField.setText("draft-secret");

            assertEquals("", SecureKeyStore.load(channel.secretKey()));

            configurable.apply();

            assertEquals("draft-secret", SecureKeyStore.load(channel.secretKey()));
        } finally {
            SecureKeyStore.store(channel.secretKey(), null);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
