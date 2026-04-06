package ch.nexoai.fabric.core.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LifecycleDefinition {

    private String objectTypeApiName;
    private String stateProperty;
    private String initialState;
    private List<State> states;
    private List<Transition> transitions;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class State {
        private String name;
        private String displayName;
        private String color;
        private boolean isFinal;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Transition {
        private String from;
        private String to;
        private String displayName;
        private String actionType;
        @Builder.Default
        private List<String> allowedRoles = List.of();
        private String condition;
    }

    public boolean canTransition(String fromState, String toState) {
        if (transitions == null) return false;
        return transitions.stream()
                .anyMatch(t -> t.getFrom().equals(fromState) && t.getTo().equals(toState));
    }

    public List<Transition> getAvailableTransitions(String currentState) {
        if (transitions == null) return List.of();
        return transitions.stream()
                .filter(t -> t.getFrom().equals(currentState))
                .toList();
    }
}
