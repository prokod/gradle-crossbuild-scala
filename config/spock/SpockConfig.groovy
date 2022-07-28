import org.spockframework.runtime.model.parallel.ExecutionMode

runner {
  parallel {
    enabled true
    defaultSpecificationExecutionMode ExecutionMode.CONCURRENT
    defaultExecutionMode ExecutionMode.SAME_THREAD
    // fixed(3)
  }
}