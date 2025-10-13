// ZhipuSettings.java
package io.github.movebrickschi.dailynewspapergenerator.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "ZhipuSettings",
    storages = @Storage("zhipu-settings.xml")
)
public class ZhipuSettings implements PersistentStateComponent<ZhipuSettings> {
    public String apiKey = "8e26dc7d757047a1a11808de105bbedc.7d77MGRZP178UWwA";
    public String promptTemplate = "请将以下Git提交信息润色成更专业的日报格式，保持简洁明了";

    @Nullable
    @Override
    public ZhipuSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ZhipuSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static ZhipuSettings getInstance() {
        return  com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(ZhipuSettings.class);
    }
}
