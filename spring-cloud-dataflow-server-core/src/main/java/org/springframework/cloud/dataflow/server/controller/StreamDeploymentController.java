/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.rest.UpdateStreamRequest;
import org.springframework.cloud.dataflow.rest.resource.StreamDeploymentResource;
import org.springframework.cloud.dataflow.server.repository.NoSuchStreamDefinitionException;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.StreamService;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.Deployer;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for deployment operations on {@link StreamDefinition}.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Patrick Peralta
 * @author Ilayaperumal Gopinathan
 * @author Marius Bogoevici
 * @author Janne Valkealahti
 */
@RestController
@RequestMapping("/streams/deployments")
@ExposesResourceFor(StreamDeploymentResource.class)
public class StreamDeploymentController {

	private final StreamService streamService;

	private final SkipperClient skipperClient;

	/**
	 * The repository this controller will use for stream CRUD operations.
	 */
	private final StreamDefinitionRepository repository;

	/**
	 * Create a {@code StreamDeploymentController} that delegates
	 * <ul>
	 * <li>CRUD operations to the provided {@link StreamDefinitionRepository}</li>
	 * <li>deployment operations to the provided {@link AppDeployer} via
	 * {@link StreamService}</li>
	 * </ul>
	 *
	 * @param repository the repository this controller will use for stream CRUD operations
	 * @param streamService the underlying StreamService to deploy the stream
	 * @param skipperClient the Skipper client to use for Skipper related services
	 */
	public StreamDeploymentController(StreamDefinitionRepository repository, StreamService streamService,
			SkipperClient skipperClient) {
		Assert.notNull(repository, "StreamDefinitionRepository must not be null");
		Assert.notNull(streamService, "StreamService must not be null");
		Assert.notNull(skipperClient, "SkipperClient must not be null");
		this.repository = repository;
		this.streamService = streamService;
		this.skipperClient = skipperClient;
	}

	/**
	 * Request un-deployment of an existing stream.
	 *
	 * @param name the name of an existing stream (required)
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void undeploy(@PathVariable("name") String name) {
		StreamDefinition stream = this.repository.findOne(name);
		if (stream == null) {
			throw new NoSuchStreamDefinitionException(name);
		}
		this.streamService.undeployStream(name);
	}

	/**
	 * Request un-deployment of all streams.
	 */
	@RequestMapping(value = "", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void undeployAll() {
		for (StreamDefinition stream : this.repository.findAll()) {
			this.streamService.undeployStream(stream.getName());
		}
	}

	/**
	 * Request deployment of an existing stream definition.
	 * @param name the name of an existing stream definition (required)
	 * @param properties the deployment properties for the stream as a comma-delimited list of
	 * key=value pairsef
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void deploy(@PathVariable("name") String name,
			@RequestBody(required = false) Map<String, String> properties) {
		this.streamService.deployStream(name, properties);
	}

	@RequestMapping(value = "/update/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void update(@PathVariable("name") String name, @RequestBody UpdateStreamRequest updateStreamRequest) {
		this.streamService.updateStream(name, updateStreamRequest);
	}

	@RequestMapping(value = "/rollback/{name}/{version}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void rollback(@PathVariable("name") String name, @PathVariable("version") int version) {
		this.streamService.rollbackStream(name, version);
	}

	@RequestMapping(value = "/manifest/{name}/{version}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public String manifest(@PathVariable("name") String name, @PathVariable("version") int version) {
		return this.skipperClient.manifest(name, version);
	}

	@RequestMapping(path = "/history/{name}/{max}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Collection<Release> history(@PathVariable("name") String releaseName,
			@PathVariable("max") String maxRevisions) {
		if (StringUtils.hasText(maxRevisions)) {
			assertMaxIsIntegerAndGreaterThanZero(maxRevisions);
			return this.skipperClient.history(releaseName, maxRevisions);
		}
		else {
			return this.skipperClient.history(releaseName).getContent();
		}
	}

	private void assertMaxIsIntegerAndGreaterThanZero(String max) {
		try {
			int maxInt = Integer.parseInt(max);
			Assert.isTrue(maxInt > 0, "The maximum number of revisions should be greater than zero.");
		}
		catch (NumberFormatException e) {
			throw new NumberFormatException("The maximum number of revisions is not an integer. Input string = " + max);
		}
	}

	@RequestMapping(path = "/platform/list", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public Collection<Deployer> platformList() {
		return this.skipperClient.listDeployers().getContent();
	}

}
