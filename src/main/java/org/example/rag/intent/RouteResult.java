package org.example.rag.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路由结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {
    private IntentEnum intent;
    private double confidence;
    private boolean uncertain;

    public static RouteResult uncertain() {
        return RouteResult.builder()
                .intent(IntentEnum.KNOWLEDGE)
                .confidence(0.0)
                .uncertain(true)
                .build();
    }

    public static RouteResult of(IntentEnum intent, double confidence) {
        return RouteResult.builder()
                .intent(intent)
                .confidence(confidence)
                .uncertain(false)
                .build();
    }
}