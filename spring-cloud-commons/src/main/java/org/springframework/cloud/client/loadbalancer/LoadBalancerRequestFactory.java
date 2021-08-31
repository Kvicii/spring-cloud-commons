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

import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Creates {@link LoadBalancerRequest}s for {@link LoadBalancerInterceptor} and
 * {@link RetryLoadBalancerInterceptor}. Applies {@link LoadBalancerRequestTransformer}s
 * to the intercepted {@link HttpRequest}.
 *
 * @author William Tran
 *
 */
public class LoadBalancerRequestFactory {

	private LoadBalancerClient loadBalancer;

	private List<LoadBalancerRequestTransformer> transformers;

	public LoadBalancerRequestFactory(LoadBalancerClient loadBalancer,
			List<LoadBalancerRequestTransformer> transformers) {
		this.loadBalancer = loadBalancer;
		this.transformers = transformers;
	}

	public LoadBalancerRequestFactory(LoadBalancerClient loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	public LoadBalancerRequest<ClientHttpResponse> createRequest(final HttpRequest request, final byte[] body,
			final ClientHttpRequestExecution execution) {
		return new LoadBalancerRequest<ClientHttpResponse>() {
			/**
			 * 对指定的Server发起HTTP请求
			 *
			 * @param instance
			 * @return
			 * @throws Exception
			 */
			@Override
			public ClientHttpResponse apply(ServiceInstance instance) throws Exception {
				// 将HttpRequest和ServiceInstance封装为了一个ServiceRequestWrapper
				HttpRequest serviceRequest = new ServiceRequestWrapper(request, instance, LoadBalancerRequestFactory.this.loadBalancer);
				if (LoadBalancerRequestFactory.this.transformers != null) {
					for (LoadBalancerRequestTransformer transformer : LoadBalancerRequestFactory.this.transformers) {
						serviceRequest = transformer.transformRequest(serviceRequest, instance);
					}
				}
				// 将ServiceRequestWrapper交给ClientHttpRequestExecution执行
				// 执行spring-web的源码 使用底层的http组件 从ServiceRequestWrapper中获取出来了对应的真正的请求URL地址 发起一次请求
				// 真正的核心代码位于ServiceRequestWrapper中
				return execution.execute(serviceRequest, body);
			}
		};
	}

}
