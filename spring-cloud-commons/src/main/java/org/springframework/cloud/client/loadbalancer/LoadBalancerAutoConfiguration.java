/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.client.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for blocking client-side load balancing.
 * <p>
 * 同步请求
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Will Tran
 * @author Gang Li
 * @author Olga Maciaszek-Sharma
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(LoadBalancerClient.class)
@EnableConfigurationProperties(LoadBalancerProperties.class)
public class LoadBalancerAutoConfiguration {

	// 在项目代码中 会实例化一个RestTemplate的Bean 一旦实例化后 就会放入这个restTemplates集合中
	@LoadBalanced
	@Autowired(required = false)
	private List<RestTemplate> restTemplates = Collections.emptyList();

	@Autowired(required = false)
	private List<LoadBalancerRequestTransformer> transformers = Collections.emptyList();

	@Bean
	public SmartInitializingSingleton loadBalancedRestTemplateInitializerDeprecated(
			final ObjectProvider<List<RestTemplateCustomizer>> restTemplateCustomizers) {
		return new SmartInitializingSingleton() {
			// 和初始化相关 一旦系统启动 在完成Bean的实例化以后 一定会执行
			@Override
			public void afterSingletonsInstantiated() {
				restTemplateCustomizers.ifAvailable(customizers -> {
					for (RestTemplate restTemplate : LoadBalancerAutoConfiguration.this.restTemplates) {
						// 处理每个RestTemplate 再对每个负责定制化RestTemplate的Customizer组件进行遍历 去定制化RestTemplate
						for (RestTemplateCustomizer customizer : customizers) {
							customizer.customize(restTemplate);
						}
					}
				});
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean
	public LoadBalancerRequestFactory loadBalancerRequestFactory(LoadBalancerClient loadBalancerClient) {
		return new LoadBalancerRequestFactory(loadBalancerClient, this.transformers);
	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(RetryMissingOrDisabledCondition.class)
	static class LoadBalancerInterceptorConfig {

		/**
		 * 返回为每个RestTemplate定制的拦截器
		 *
		 * @param loadBalancerClient 通过方法直接传入了LoadBalanceClient 一般是
		 *                           有一个实现类 在其他地方 对这个实现类构造了一个实例 打成了一个Spring的Bean 才可以纳入Spring的管理
		 * @param requestFactory
		 * @return
		 */
		@Bean
		public LoadBalancerInterceptor loadBalancerInterceptor(LoadBalancerClient loadBalancerClient,
				LoadBalancerRequestFactory requestFactory) {
			return new LoadBalancerInterceptor(loadBalancerClient, requestFactory);
		}

		/**
		 * RestTemplateCustomizer是对RestTemplate进行定制化的组件
		 *
		 * @param loadBalancerInterceptor 从上一个方法返回的定制拦截器
		 * @return
		 */
		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(final LoadBalancerInterceptor loadBalancerInterceptor) {
			return new RestTemplateCustomizer() {
				// 对RestTemplate进行定制化(添加拦截器) 后续对这个RestTemplate的调用都会先执行拦截器中的逻辑 再进行HTTP调用
				@Override
				public void customize(RestTemplate restTemplate) {
					List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
					list.add(loadBalancerInterceptor);
					restTemplate.setInterceptors(list);
				}
			};
		}

	}

	private static class RetryMissingOrDisabledCondition extends AnyNestedCondition {

		RetryMissingOrDisabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
		static class RetryTemplateMissing {

		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", havingValue = "false")
		static class RetryDisabled {

		}

	}

	/**
	 * Auto configuration for retry mechanism.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RetryTemplate.class)
	public static class RetryAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public LoadBalancedRetryFactory loadBalancedRetryFactory() {
			return new LoadBalancedRetryFactory() {
			};
		}

	}

	/**
	 * Auto configuration for retry intercepting mechanism.
	 * <p>
	 * 和RetryTemplate相关 和RestTemplate无关
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RetryTemplate.class)
	@ConditionalOnBean(ReactiveLoadBalancer.Factory.class)
	@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", matchIfMissing = true)
	public static class RetryInterceptorAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public RetryLoadBalancerInterceptor loadBalancerInterceptor(LoadBalancerClient loadBalancerClient,
				LoadBalancerProperties properties, LoadBalancerRequestFactory requestFactory,
				LoadBalancedRetryFactory loadBalancedRetryFactory,
				ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory) {
			return new RetryLoadBalancerInterceptor(loadBalancerClient, properties, requestFactory,
					loadBalancedRetryFactory, loadBalancerFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(
				final RetryLoadBalancerInterceptor loadBalancerInterceptor) {
			return new RestTemplateCustomizer() {
				@Override
				public void customize(RestTemplate restTemplate) {
					List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
					list.add(loadBalancerInterceptor);
					restTemplate.setInterceptors(list);
				}
			};
		}

	}

}
