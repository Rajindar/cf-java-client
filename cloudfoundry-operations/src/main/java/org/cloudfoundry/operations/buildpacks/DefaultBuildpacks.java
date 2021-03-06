/*
 * Copyright 2013-2016 the original author or authors.
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

package org.cloudfoundry.operations.buildpacks;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.buildpacks.BuildpackEntity;
import org.cloudfoundry.client.v2.buildpacks.BuildpackResource;
import org.cloudfoundry.client.v2.buildpacks.CreateBuildpackResponse;
import org.cloudfoundry.client.v2.buildpacks.ListBuildpacksRequest;
import org.cloudfoundry.client.v2.buildpacks.UploadBuildpackRequest;
import org.cloudfoundry.client.v2.buildpacks.UploadBuildpackResponse;
import org.cloudfoundry.util.PaginationUtils;
import org.cloudfoundry.util.ResourceUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.Optional;

import static org.cloudfoundry.util.tuple.TupleUtils.function;

public final class DefaultBuildpacks implements Buildpacks {

    private final Mono<CloudFoundryClient> cloudFoundryClient;

    public DefaultBuildpacks(Mono<CloudFoundryClient> cloudFoundryClient) {
        this.cloudFoundryClient = cloudFoundryClient;
    }

    @Override
    public Mono<Void> create(CreateBuildpackRequest request) {
        return this.cloudFoundryClient
            .then(cloudFoundryClient -> Mono.when(
                Mono.just(cloudFoundryClient),
                requestCreateBuildpack(cloudFoundryClient, request.getName(), request.getPosition(), request.getEnable())
            ))
            .then(function((cloudFoundryClient, response) -> requestUploadBuildpackBits(cloudFoundryClient, ResourceUtils.getId(response), request.getFileName(), request.getBuildpack())))
            .then();
    }

    @Override
    public Flux<Buildpack> list() {
        return this.cloudFoundryClient
            .flatMap(DefaultBuildpacks::requestBuildpacks)
            .map(DefaultBuildpacks::toBuildpackResource);
    }

    private static Flux<BuildpackResource> requestBuildpacks(CloudFoundryClient cloudFoundryClient) {
        return PaginationUtils
            .requestResources(page -> cloudFoundryClient.buildpacks()
                .list(ListBuildpacksRequest.builder()
                    .page(page)
                    .build()));
    }

    private static Mono<CreateBuildpackResponse> requestCreateBuildpack(CloudFoundryClient cloudFoundryClient, String buildpackName, Integer position, Boolean enable) {
        return cloudFoundryClient.buildpacks()
            .create(org.cloudfoundry.client.v2.buildpacks.CreateBuildpackRequest
                .builder()
                .name(buildpackName)
                .position(position)
                .enabled(Optional.ofNullable(enable).orElse(true))
                .build());
    }

    private static Mono<UploadBuildpackResponse> requestUploadBuildpackBits(CloudFoundryClient cloudFoundryClient, String buildpackId, String filename, InputStream buildpack) {
        return cloudFoundryClient.buildpacks()
            .upload(UploadBuildpackRequest.builder()
                .buildpackId(buildpackId)
                .filename(filename)
                .buildpack(buildpack)
                .build());
    }

    private static Buildpack toBuildpackResource(BuildpackResource resource) {
        BuildpackEntity entity = ResourceUtils.getEntity(resource);

        return Buildpack.builder()
            .enabled(entity.getEnabled())
            .filename(entity.getFilename())
            .id(ResourceUtils.getId(resource))
            .locked(entity.getLocked())
            .name(entity.getName())
            .position(entity.getPosition())
            .build();
    }

}
