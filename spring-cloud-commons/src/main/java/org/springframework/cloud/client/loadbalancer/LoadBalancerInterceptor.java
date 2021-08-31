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

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;

/**
 * 拦截RestTemplate的请求 先执行拦截器中的逻辑
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Ryan Baxter
 * @author William Tran
 */
public class LoadBalancerInterceptor implements ClientHttpRequestInterceptor {

	private LoadBalancerClient loadBalancer;

	private LoadBalancerRequestFactory requestFactory;

	public LoadBalancerInterceptor(LoadBalancerClient loadBalancer, LoadBalancerRequestFactory requestFactory) {
		this.loadBalancer = loadBalancer;
		this.requestFactory = requestFactory;
	}

	public LoadBalancerInterceptor(LoadBalancerClient loadBalancer) {
		// for backwards compatibility
		this(loadBalancer, new LoadBalancerRequestFactory(loadBalancer));
	}

	/**
	 * 将请求路径封装到HttpRequest 对RestTemplate的HTTP调用 最终都会执行到此方法中的逻辑 例:
	 * `restTemplate.getForObject("http://ServiceA/sayHello/leo", String.class);`
	 * 相当于执行的是拦截器中的intercept方法 而不是RestTemplate原生的逻辑
	 *
	 * @param request
	 * @param body      请求体
	 * @param execution HTTP通信组件
	 * @return
	 * @throws IOException
	 */
	@Override
	public ClientHttpResponse intercept(final HttpRequest request, final byte[] body,
			final ClientHttpRequestExecution execution) throws IOException {
		// 获取到的是请求路径(http://ServiceA/sayHello/leo)
		final URI originalUri = request.getURI();
		// 获取到的是服务名称(ServiceA)
		String serviceName = originalUri.getHost();
		Assert.state(serviceName != null, "Request URI does not contain a valid hostname: " + originalUri);
		// 传入服务名称和构造出的LoadBalancerRequest 执行LoadBalanceClient对应的负载均衡请求
		return this.loadBalancer.execute(serviceName, this.requestFactory.createRequest(request, body, execution));
	}

}
