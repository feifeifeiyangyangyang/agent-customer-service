package com.zhifutong.customer.client;

import com.zhifutong.customer.config.AppProperties;
public class DeterministicEmbeddingClient implements EmbeddingClient {
    private final int dimension;

    public DeterministicEmbeddingClient(AppProperties properties) {
        this.dimension = properties.getEmbedding().getDimension();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(codePoint -> vector[Math.floorMod(codePoint, dimension)] += 1.0f);
        for (int i = 0; i < normalized.length() - 1; i++) {
            int first = normalized.charAt(i);
            int second = normalized.charAt(i + 1);
            if (Character.isLetterOrDigit(first) && Character.isLetterOrDigit(second)) {
                vector[Math.floorMod(first * 31 + second, dimension)] += 0.5f;
            }
        }
        return normalize(vector);
    }

    private float[] normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
