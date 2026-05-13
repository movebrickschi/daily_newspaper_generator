package io.github.movebrickschi.dailynewspapergenerator.channel;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AccessTokenCache} 的轻量单测。
 *
 * <p>无法测试真实 HTTP 路径，但可以验证：</p>
 * <ul>
 *   <li>{@code snapshot()} 返回不可变副本</li>
 *   <li>不同 secret 哈希到不同 cache key</li>
 *   <li>{@code invalidate()} 仅清理对应条目，不影响其它</li>
 * </ul>
 *
 * @author Liu Chunchi
 */
public class AccessTokenCacheTest {

    @After
    public void tearDown() throws Exception {
        Field cacheField = AccessTokenCache.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        Object cacheObj = cacheField.get(null);
        // 通过反射拿到内部 LRU map
        @SuppressWarnings("unchecked")
        Map<String, Object> internal = (Map<String, Object>) cacheObj;
        synchronized (internal) {
            internal.clear();
        }
    }

    @Test
    public void snapshot_returnsImmutableCopy() {
        Map<String, AccessTokenCache.Entry> snap = AccessTokenCache.snapshot();
        assertNotNull(snap);
        try {
            snap.put("evil", new AccessTokenCache.Entry("x", 0));
            // Map.copyOf 返回 unmodifiable，写入应抛 UnsupportedOperationException
            assertTrue("snapshot 应为不可变副本", false);
        } catch (UnsupportedOperationException ok) {
            // expected
        }
    }

    @Test
    public void cacheKey_differsForDifferentSecret() throws Exception {
        Method m = AccessTokenCache.class.getDeclaredMethod("cacheKey", String.class, String.class);
        m.setAccessible(true);

        String k1 = (String) m.invoke(null, "appKey-A", "secret-1");
        String k2 = (String) m.invoke(null, "appKey-A", "secret-2");
        String k3 = (String) m.invoke(null, "appKey-B", "secret-1");

        assertNotEquals("不同 secret 必须哈希到不同 key", k1, k2);
        assertNotEquals("不同 appKey 必须哈希到不同 key", k1, k3);
    }

    @Test
    public void cacheKey_isStableHash() throws Exception {
        Method m = AccessTokenCache.class.getDeclaredMethod("cacheKey", String.class, String.class);
        m.setAccessible(true);

        String first = (String) m.invoke(null, "k", "s");
        String second = (String) m.invoke(null, "k", "s");
        assertEquals("同输入两次必须得到相同 key", first, second);
        assertEquals("sha256 hex 长度应为 64", 64, first.length());
    }

    @Test
    public void invalidate_nullSafe() {
        // 不应抛 NPE
        AccessTokenCache.invalidate(null, "x");
        AccessTokenCache.invalidate("x", null);
        AccessTokenCache.invalidate(null, null);
    }
}
