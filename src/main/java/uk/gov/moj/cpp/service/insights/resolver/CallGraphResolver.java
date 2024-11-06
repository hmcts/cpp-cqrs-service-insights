package uk.gov.moj.cpp.service.insights.resolver;

import uk.gov.moj.cpp.service.insights.model.MethodInfo;

import java.util.List;
import java.util.Optional;

public interface CallGraphResolver {
    void resolveCallGraph();

    List<String> getCallStack(String methodSignature);

    Optional<MethodInfo> findMethodInfo(String methodSignature);
}
