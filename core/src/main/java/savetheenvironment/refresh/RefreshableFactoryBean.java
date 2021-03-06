/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package savetheenvironment.refresh;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

import javax.inject.Provider;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Josh Long
 */
public class RefreshableFactoryBean<T> implements FactoryBean<T> {

    private final Method disposeMethod =
            ReflectionUtils.findMethod(DisposableBean.class, "destroy");

    private final Method onApplicationEventMethod =
            ReflectionUtils.findMethod(ApplicationListener.class, "onApplicationEvent", ApplicationEvent.class);

    private final Method refreshMethod =
            ReflectionUtils.findMethod(Refreshable.class, "refresh");

    private final AtomicReference<T> atomicReference = new AtomicReference<T>();

    private Provider<T> provider;

    public RefreshableFactoryBean(Provider<T> provider) {
        this.provider = provider;
    }

    public RefreshableFactoryBean(T initialRef, Provider<T> provider) {
        this.provider = provider;
        this.atomicReference.set(initialRef);
    }

    protected void updateBeanReference() {
        T existingReference = atomicReference.get();
        if (null != existingReference && existingReference instanceof DisposableBean) {
            try {
                ((DisposableBean) existingReference).destroy();
            } catch (Exception e) {
                // don't care
            }
        }
        this.atomicReference.set(this.provider.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() throws Exception {

        if (null == this.atomicReference.get()) {
            updateBeanReference();
        }

        T beanReference = atomicReference.get();

        ProxyFactory pf = new ProxyFactory(beanReference);
        pf.setProxyTargetClass(true);
        List<Class<?>> listOfClasses = new ArrayList<Class<?>>();
        listOfClasses.add(Refreshable.class);
        listOfClasses.add(ApplicationListener.class);
        listOfClasses.add(DisposableBean.class);
        listOfClasses.addAll(Arrays.asList(beanReference.getClass().getInterfaces()));
        pf.setInterfaces(listOfClasses.toArray(new Class[listOfClasses.size()]));
        pf.addAdvice(new MethodInterceptor() {


            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                // if someone externally calls refresh, then rebuild the reference
                if (method.equals(refreshMethod)) {
                    updateBeanReference();
                    return atomicReference.get();
                } else if (method.equals(disposeMethod)) {
                    T atomicT = atomicReference.get();
                    if (atomicT instanceof DisposableBean) {
                        ((DisposableBean) atomicT).destroy();
                    }
                } else if (method.equals(onApplicationEventMethod)) {
                    Object[] arguments = invocation.getArguments();
                    if (arguments[0] instanceof RefreshEvent) {
                        updateBeanReference();
                    }
                    return null;
                }
                // otherwise send the request on through to the underlying object unchanged
                Object ref = atomicReference.get();
                return method.invoke(ref, invocation.getArguments());

            }
        });
        Object o = pf.getProxy();
        return (T) o;
    }

    @Override
    public Class<?> getObjectType() {
        return Object.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public static interface Refreshable<T> {
        T refresh();
    }
}
