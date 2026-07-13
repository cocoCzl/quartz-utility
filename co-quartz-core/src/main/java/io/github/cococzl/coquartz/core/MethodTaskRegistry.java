package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node-local registry that maps stable Quartz task identities to Spring method invokers.
 */
public class MethodTaskRegistry {

    private final ApplicationContext applicationContext;
    private volatile Map<JobKey, MethodTaskInvoker> invokers = Map.of();

    public MethodTaskRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void replaceDefinitions(List<QuartzJobDefinition> definitions) {
        Map<JobKey, MethodTaskInvoker> replacements = new LinkedHashMap<>();
        for (QuartzJobDefinition definition : definitions) {
            if (!definition.isMethodTask()) {
                continue;
            }
            JobKey jobKey = JobKey.jobKey(definition.getName(), definition.getGroup());
            String beanName = definition.getMethodBeanName();
            String methodName = definition.getMethodName();
            Object bean = applicationContext.getBean(beanName);
            Method targetMethod = ReflectionUtils.findMethod(AopUtils.getTargetClass(bean), methodName);
            if (targetMethod == null) {
                throw new CoQuartzConfigurationException("Method task target no longer exists: "
                        + beanName + "#" + methodName);
            }
            Method invocableMethod;
            try {
                invocableMethod = AopUtils.selectInvocableMethod(targetMethod, bean.getClass());
            } catch (IllegalStateException e) {
                throw new CoQuartzConfigurationException("Method task is not invocable through the Spring bean: "
                        + beanName + "#" + methodName, e);
            }
            ReflectionUtils.makeAccessible(invocableMethod);
            replacements.put(jobKey, new MethodTaskInvoker(beanName, invocableMethod));
        }
        invokers = Map.copyOf(replacements);
    }

    public void invoke(JobKey jobKey) throws JobExecutionException {
        MethodTaskInvoker invoker = invokers.get(jobKey);
        if (invoker == null) {
            throw new JobExecutionException("No method task invoker is registered for " + jobKey);
        }
        Object bean = applicationContext.getBean(invoker.beanName());
        try {
            invoker.method().invoke(bean);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof JobExecutionException jobExecutionException) {
                throw jobExecutionException;
            }
            throw new JobExecutionException("Method task execution failed: " + jobKey, cause);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new JobExecutionException("Failed to invoke method task: " + jobKey, e);
        }
    }

    private record MethodTaskInvoker(String beanName, Method method) {
    }
}
