package org.springframework.cloud.gateway.ratelimiter.cloudfoundry;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.ratelimiter.cluster.ClusterMembersDiscovery;
import org.springframework.cloud.gateway.ratelimiter.cluster.MemberInfo;

public class CloudFoundryInternalHostsDiscovery implements ClusterMembersDiscovery {

	private static final Logger logger = LoggerFactory.getLogger(CloudFoundryInternalHostsDiscovery.class);
	private static final long MAX_RETRIES = 5;

	private final String internalHost;
	private final int instanceIndex;
	private final MemberAvailabilityChecker availabilityChecker;

	public CloudFoundryInternalHostsDiscovery(List<String> uris, int instanceIndex, MemberAvailabilityChecker availabilityChecker) {
		this.instanceIndex = instanceIndex;
		this.availabilityChecker = availabilityChecker;
		this.internalHost = uris
				.stream()
				.filter(uri -> uri.endsWith(".apps.internal"))
				.findFirst()
				.orElseThrow(() ->
						new IllegalStateException(String.format("No internal route found in %s, add <app-name>.apps.internal route", String.join(", ", uris))));
	}

	public Mono<MemberInfo> thisMember() {
		return Mono.just(new MemberInfo(instanceIndex + "." + internalHost));
	}

	public Mono<List<MemberInfo>> discover() {
		return Flux.range(0, instanceIndex + 1)
		           .map(idx -> idx + "." + internalHost)
		           .map(MemberInfo::new)
		           .flatMap(availabilityChecker::check)
		           .retryBackoff(MAX_RETRIES, Duration.ofSeconds(1), Duration.ofSeconds(3))
		           .collectList()
		           .doOnNext(memberInfos -> logger.debug("Using members list {}", memberInfos))
		           .cache();
	}
}
