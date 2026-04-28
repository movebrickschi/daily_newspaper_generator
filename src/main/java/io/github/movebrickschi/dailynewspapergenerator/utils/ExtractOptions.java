package io.github.movebrickschi.dailynewspapergenerator.utils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交抽取的参数集合。可序列化为对话框选项 / Action 调用入参。
 *
 * @author Liu Chunchi
 */
public class ExtractOptions {

    public enum DateRange {
        TODAY,
        YESTERDAY,
        THIS_WEEK,
        LAST_WEEK,
        LAST_7_DAYS,
        CUSTOM
    }

    public DateRange range = DateRange.TODAY;

    /** 自定义起止；仅当 range == CUSTOM 时生效，包含两端日期。 */
    public LocalDate customSince;
    public LocalDate customUntil;

    /** 作者过滤；空表示当前 git config user.name。多个之间是 OR 关系。 */
    public List<String> authors = new ArrayList<>();

    public boolean skipMerges = true;
    public boolean skipReverts = false;
    public boolean skipWip = false;

    /** 是否按 Conventional Commits 前缀分组。 */
    public boolean classifyByConventional = true;

    /** 是否输出 commit 数量与代码增删行统计。 */
    public boolean includeStats = true;

    public record DateBounds(LocalDate since, LocalDate until) {
    }

    public DateBounds computeBounds() {
        LocalDate today = LocalDate.now();
        return switch (range) {
            case TODAY -> new DateBounds(today, today);
            case YESTERDAY -> {
                LocalDate y = today.minusDays(1);
                yield new DateBounds(y, y);
            }
            case THIS_WEEK -> new DateBounds(GitCommitExtractor.startOfWeek(today), today);
            case LAST_WEEK -> {
                LocalDate lwStart = GitCommitExtractor.startOfWeek(today).minusWeeks(1);
                yield new DateBounds(lwStart, lwStart.plusDays(6));
            }
            case LAST_7_DAYS -> new DateBounds(today.minusDays(6), today);
            case CUSTOM -> {
                LocalDate s = customSince != null ? customSince : today;
                LocalDate u = customUntil != null ? customUntil : today;
                if (u.isBefore(s)) {
                    LocalDate tmp = s;
                    s = u;
                    u = tmp;
                }
                yield new DateBounds(s, u);
            }
        };
    }

    public static ExtractOptions today() {
        return new ExtractOptions();
    }
}
