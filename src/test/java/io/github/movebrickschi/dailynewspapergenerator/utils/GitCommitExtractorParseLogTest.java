package io.github.movebrickschi.dailynewspapergenerator.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitCommitExtractor#parseLog(String, ExtractOptions)} 的协议解析单测。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>空输入 / null 输入</li>
 *   <li>单条 / 多条 record（0x1e 分隔）</li>
 *   <li>各字段（hash/author/time/subject/body）正确解析</li>
 *   <li>{@code --shortstat} 行 additions/deletions 提取</li>
 *   <li>{@code skipReverts} / {@code skipWip} 过滤逻辑</li>
 *   <li>{@code classifyByConventional} 关闭时 body 保留 prefix</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class GitCommitExtractorParseLogTest {

    private static final char US = '\u001f';
    private static final char RS = '\u001e';

    private static ExtractOptions baseOptions() {
        ExtractOptions o = new ExtractOptions();
        o.includeStats = false;
        o.classifyByConventional = false;
        o.skipReverts = false;
        o.skipWip = false;
        return o;
    }

    @Test
    public void emptyInput_returnsEmptyList() {
        assertTrue(GitCommitExtractor.parseLog("", baseOptions()).isEmpty());
        assertTrue(GitCommitExtractor.parseLog(null, baseOptions()).isEmpty());
    }

    @Test
    public void singleRecord_parsesAllFields() {
        String raw = "abc123" + US + "Alice" + US + "2026-04-20 10:30:00 +0800" + US
                + "feat(api): support v2 endpoint" + US + "Detailed body line 1\nbody line 2" + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, baseOptions());

        assertEquals(1, commits.size());
        GitCommitExtractor.RawCommit c = commits.get(0);
        assertEquals("abc123", c.getHash());
        assertEquals("Alice", c.getAuthor());
        assertEquals("feat(api): support v2 endpoint", c.getSubject());
        assertNotNull(c.getBody());
        assertTrue("body should contain first line", c.getBody().contains("body line 1"));
        assertTrue("body should contain second line", c.getBody().contains("body line 2"));
    }

    @Test
    public void multipleRecords_parsedInOrder() {
        String raw =
                "h1" + US + "A" + US + "t1" + US + "first" + US + "" + RS
                        + "h2" + US + "B" + US + "t2" + US + "second" + US + "" + RS
                        + "h3" + US + "C" + US + "t3" + US + "third" + US + "" + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, baseOptions());

        assertEquals(3, commits.size());
        assertEquals("first", commits.get(0).getSubject());
        assertEquals("second", commits.get(1).getSubject());
        assertEquals("third", commits.get(2).getSubject());
    }

    @Test
    public void shortstat_addsAndDeletionsParsed() {
        ExtractOptions opts = baseOptions();
        opts.includeStats = true;

        String tail = "body\n 3 files changed, 42 insertions(+), 7 deletions(-)\n";
        String raw = "h1" + US + "A" + US + "t1" + US + "subj" + US + tail + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, opts);

        assertEquals(1, commits.size());
        assertEquals(42, commits.get(0).getAdditions());
        assertEquals(7, commits.get(0).getDeletions());
        // shortstat 行应已从 body 中剥离
        assertTrue("body should not contain stat line",
                !commits.get(0).getBody().contains("insertion"));
        assertTrue(commits.get(0).getBody().contains("body"));
    }

    @Test
    public void skipReverts_filtersRevertSubject() {
        ExtractOptions opts = baseOptions();
        opts.skipReverts = true;

        String raw =
                "h1" + US + "A" + US + "t1" + US + "feat: x" + US + "" + RS
                        + "h2" + US + "A" + US + "t2" + US + "Revert: undo y" + US + "" + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, opts);

        assertEquals(1, commits.size());
        assertEquals("feat: x", commits.get(0).getSubject());
    }

    @Test
    public void skipWip_filtersWipAndBracketWip() {
        ExtractOptions opts = baseOptions();
        opts.skipWip = true;

        String raw =
                "h1" + US + "A" + US + "t1" + US + "WIP: ongoing" + US + "" + RS
                        + "h2" + US + "A" + US + "t2" + US + "feat: stable" + US + "" + RS
                        + "h3" + US + "A" + US + "t3" + US + "fix [wip] not yet" + US + "" + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, opts);

        assertEquals(1, commits.size());
        assertEquals("feat: stable", commits.get(0).getSubject());
    }

    @Test
    public void malformedRecord_skippedSilently() {
        String raw =
                "garbage_record_with_no_separator" + RS
                        + "h1" + US + "A" + US + "t1" + US + "valid" + US + "" + RS;

        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, baseOptions());

        assertEquals("garbage record should be skipped", 1, commits.size());
        assertEquals("valid", commits.get(0).getSubject());
    }

    @Test
    public void emptyBodyField_yieldsEmptyBody() {
        String raw = "h1" + US + "A" + US + "t1" + US + "subject only" + US + "" + RS;
        List<GitCommitExtractor.RawCommit> commits = GitCommitExtractor.parseLog(raw, baseOptions());
        assertEquals(1, commits.size());
        // body 字段为空时不会为 null，而是空串
        assertNotNull(commits.get(0).getBody());
    }
}
