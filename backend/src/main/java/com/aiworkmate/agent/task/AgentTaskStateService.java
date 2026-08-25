package com.aiworkmate.agent.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentTaskStateService {
    private final AgentTaskMapper taskMapper;
    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    @Transactional
    public boolean transition(AgentTask task, AgentTaskStatus target) {
        AgentTaskStatus current = AgentTaskStatus.valueOf(task.getStatus());
        stateMachine.requireTransition(current, target);
        int changed = taskMapper.transition(task.getId(), current.name(), target.name(), task.getVersion());
        if (changed == 1) {
            task.setStatus(target.name());
            task.setVersion(task.getVersion() + 1);
            return true;
        }
        return false;
    }
}
