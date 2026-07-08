export function buildLocalDockerDependencyHealthPlan({ env = process.env } = {}) {
  return [
    {
      name: 'postgres',
      containerName: env.LOCAL_DOCKER_POSTGRES_CONTAINER ?? 'ir-postgres',
    },
    {
      name: 'redis',
      containerName: env.LOCAL_DOCKER_REDIS_CONTAINER ?? 'ir-redis',
    },
    {
      name: 'minio',
      containerName: env.LOCAL_DOCKER_MINIO_CONTAINER ?? 'ir-minio',
    },
    {
      name: 'rocketmq-namesrv',
      containerName: env.LOCAL_DOCKER_ROCKETMQ_NAMESRV_CONTAINER ?? 'intelligent-report-system-rocketmq-namesrv-1',
    },
    {
      name: 'rocketmq-broker',
      containerName: env.LOCAL_DOCKER_ROCKETMQ_BROKER_CONTAINER ?? 'intelligent-report-system-rocketmq-broker-1',
    },
    {
      name: 'opensearch',
      containerName: env.LOCAL_DOCKER_OPENSEARCH_CONTAINER ?? 'ir-opensearch',
    },
    {
      name: 'milvus',
      containerName: env.LOCAL_DOCKER_MILVUS_CONTAINER ?? 'ir-milvus',
    },
    {
      name: 'java-api',
      containerName: env.LOCAL_DOCKER_JAVA_API_CONTAINER ?? 'ir-java-smoke',
    },
    {
      name: 'higress',
      containerName: env.LOCAL_DOCKER_HIGRESS_CONTAINER ?? 'ir-higress',
      allowNoHealthcheck: true,
    },
    {
      name: 'etcd',
      containerName: env.LOCAL_DOCKER_ETCD_CONTAINER ?? 'ir-etcd',
      allowNoHealthcheck: true,
    },
  ];
}

export function buildDockerInspectArgs(containerName) {
  return ['inspect', '--format', '{{json .State}}', containerName];
}

export function parseDockerInspectState(rawState) {
  const parsed = JSON.parse(rawState);
  return {
    status: parsed.Status ?? 'unknown',
    running: parsed.Running === true,
    exitCode: Number.isInteger(parsed.ExitCode) ? parsed.ExitCode : null,
    error: parsed.Error ?? '',
    ...(parsed.Health
      ? {
          healthStatus: parsed.Health.Status ?? 'unknown',
          healthFailingStreak: Number.isInteger(parsed.Health.FailingStreak)
            ? parsed.Health.FailingStreak
            : null,
        }
      : {}),
  };
}

function summarizeDependency(dependency, state) {
  if (!state) {
    return {
      ...dependency,
      passed: false,
      classification: 'container-missing',
    };
  }

  if (state.running !== true) {
    return {
      ...dependency,
      passed: false,
      classification: 'container-not-running',
      state,
    };
  }

  if (state.healthStatus === 'healthy') {
    return {
      ...dependency,
      passed: true,
      classification: 'container-healthy',
      state,
    };
  }

  if (state.healthStatus && state.healthStatus !== 'healthy') {
    return {
      ...dependency,
      passed: false,
      classification: 'container-unhealthy',
      state,
    };
  }

  if (dependency.allowNoHealthcheck === true) {
    return {
      ...dependency,
      passed: true,
      classification: 'container-running-no-healthcheck-allowed',
      state,
    };
  }

  return {
    ...dependency,
    passed: false,
    classification: 'container-healthcheck-missing',
    state,
  };
}

export function summarizeLocalDockerDependencyHealth({
  plan = buildLocalDockerDependencyHealthPlan(),
  statesByContainer = new Map(),
} = {}) {
  const results = plan.map((dependency) => (
    summarizeDependency(dependency, statesByContainer.get(dependency.containerName))
  ));
  const failedResults = results.filter((result) => result.passed !== true);

  return {
    passed: failedResults.length === 0,
    classification: failedResults.length === 0
      ? 'local-docker-dependencies-healthy'
      : 'local-docker-dependencies-unhealthy',
    resultCount: results.length,
    results,
    failedResults,
  };
}
