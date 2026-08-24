package com.workflowautomation.jiraplanner.automation.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, String> {

    Optional<AutomationExecution> findFirstByEpicKeyAndExecutionStatusInOrderByCreatedAtDesc(
            String epicKey, Collection<ExecutionStatus> statuses);

    Optional<AutomationExecution> findFirstByEpicKeyOrderByCreatedAtDesc(String epicKey);

    List<AutomationExecution> findAllByOrderByUpdatedAtDesc();

    List<AutomationExecution> findByExecutionStatusAndPhase(ExecutionStatus status, ExecutionPhase phase);

    List<AutomationExecution> findByExecutionStatus(ExecutionStatus status);
}
