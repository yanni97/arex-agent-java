package io.arex.inst.cache.caffeine;

import io.arex.agent.bootstrap.model.MockResult;
import io.arex.inst.cache.util.CacheLoaderUtil;
import io.arex.inst.dynamic.common.DynamicClassExtractor;
import io.arex.inst.extension.MethodInstrumentation;
import io.arex.inst.extension.TypeInstrumentation;
import io.arex.inst.runtime.context.ContextManager;
import io.arex.inst.runtime.context.RepeatedCollectManager;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class CaffeineSyncInstrumentation extends TypeInstrumentation {
    @Override
    protected ElementMatcher<TypeDescription> typeMatcher() {
        return named("com.github.benmanes.caffeine.cache.BoundedLocalCache");
    }

    @Override
    public List<MethodInstrumentation> methodAdvices() {
        return Arrays.asList(new MethodInstrumentation(
                        isMethod().and(named("computeIfAbsent")).and(takesArguments(4)),
                        GetAdvice.class.getName()),
                new MethodInstrumentation(isMethod().and(named("getIfPresent")).and(takesArguments(2)),
                        GetAdvice.class.getName()));
    }

    public static class GetAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.Argument(0) Object key,
                                      @Advice.Origin("#m") String methodName,
                                      @Advice.Origin("#r") String methodReturnType,
                                      @Advice.Local("mockResult") MockResult mockResult,
                                      @Advice.FieldValue("cacheLoader") Object cacheLoader) {
            if (ContextManager.needRecord()) {
                RepeatedCollectManager.enter();
            }
            if (ContextManager.needReplay() && CacheLoaderUtil.needRecordOrReplay(cacheLoader)) {
                String className = CacheLoaderUtil.getLocatedClass(cacheLoader);
                DynamicClassExtractor extractor = new DynamicClassExtractor(className, methodName, new Object[]{key}, methodReturnType);
                mockResult = extractor.replayOrRealCall();
                return mockResult != null && mockResult.notIgnoreMockResult();
            }
            return false;
        }
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.Origin("#m") String methodName,
                                  @Advice.Argument(0) Object key,
                                  @Advice.Local("mockResult") MockResult mockResult,
                                  @Advice.FieldValue("cacheLoader") Object cacheLoader,
                                  @Advice.Origin("#r") String methodReturnType,
                                  @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
                                  @Advice.Thrown(readOnly = false) Throwable throwable) {
            if (mockResult != null && mockResult.notIgnoreMockResult()) {
                if (mockResult.getThrowable() != null) {
                    throwable = mockResult.getThrowable();
                } else {
                    result = mockResult.getResult();
                }
                return;
            }
            if (ContextManager.needRecord() && RepeatedCollectManager.exitAndValidate() && CacheLoaderUtil.needRecordOrReplay(cacheLoader)) {
                String className = CacheLoaderUtil.getLocatedClass(cacheLoader);
                DynamicClassExtractor extractor = new DynamicClassExtractor(className, methodName, new Object[]{key}, methodReturnType);
                extractor.recordResponse(throwable != null ? throwable : result);
            }
        }
    }
}
