package com.zhifutong.customer.client;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.exception.BusinessException;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LocalOnnxEmbeddingClient implements EmbeddingClient, AutoCloseable {
    private final AppProperties properties;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public LocalOnnxEmbeddingClient(AppProperties properties) {
        this.properties = properties;
        Path model = Path.of(properties.getEmbedding().getModelPath());
        Path tokenizerPath = Path.of(properties.getEmbedding().getTokenizerPath());
        if (!Files.exists(model) || !Files.exists(tokenizerPath)) {
            throw new BusinessException("本地 Embedding 模型或 tokenizer 缺失，请先准备模型文件，或将 EMBEDDING_MOCK_ENABLED 设置为 true");
        }

        try {
            this.environment = OrtEnvironment.getEnvironment("smart-customer-service-embedding");
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            this.session = environment.createSession(model.toString(), options);
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optTruncation(true)
                    .optMaxLength(properties.getEmbedding().getMaxTokenLength())
                    .optPadding(false)
                    .build();
        } catch (OrtException | IOException ex) {
            throw new BusinessException("本地 Embedding 模型初始化失败: " + ex.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        String safeText = text == null ? "" : text;
        Encoding encoding = tokenizer.encode(safeText);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();

        try (MapBackedTensors tensors = createInputs(inputIds, attentionMask, tokenTypeIds);
             OrtSession.Result result = session.run(tensors.values)) {
            if (result.size() == 0) {
                throw new BusinessException("本地 Embedding 模型没有返回输出");
            }
            OnnxValue value = result.get(0);
            float[] vector = extractVector(value.getValue(), attentionMask);
            normalize(vector);
            int expectedDimension = properties.getEmbedding().getDimension();
            if (expectedDimension > 0 && vector.length != expectedDimension) {
                throw new BusinessException("Embedding 维度不匹配，配置为 " + expectedDimension + "，模型输出为 " + vector.length);
            }
            return vector;
        } catch (OrtException ex) {
            throw new BusinessException("本地 Embedding 推理失败: " + ex.getMessage());
        }
    }

    private MapBackedTensors createInputs(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) throws OrtException {
        Map<String, OnnxTensor> inputs = new HashMap<>();
        Map<String, NodeInfo> inputInfo = session.getInputInfo();
        for (String name : session.getInputNames()) {
            if ("input_ids".equals(name)) {
                inputs.put(name, createTensor(name, inputIds, inputInfo));
            } else if ("attention_mask".equals(name)) {
                inputs.put(name, createTensor(name, attentionMask, inputInfo));
            } else if ("token_type_ids".equals(name)) {
                inputs.put(name, createTensor(name, tokenTypeIds, inputInfo));
            }
        }
        if (!inputs.containsKey("input_ids")) {
            throw new BusinessException("本地 Embedding 模型缺少 input_ids 输入");
        }
        return new MapBackedTensors(inputs);
    }

    private OnnxTensor createTensor(String name, long[] values, Map<String, NodeInfo> inputInfo) throws OrtException {
        long[] shape = new long[] {1, values.length};
        OnnxJavaType inputType = tensorType(name, inputInfo);
        if (inputType == OnnxJavaType.INT32) {
            int[] intValues = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                intValues[i] = Math.toIntExact(values[i]);
            }
            return OnnxTensor.createTensor(environment, IntBuffer.wrap(intValues), shape);
        }
        return OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape);
    }

    private OnnxJavaType tensorType(String name, Map<String, NodeInfo> inputInfo) {
        NodeInfo info = inputInfo.get(name);
        if (info != null && info.getInfo() instanceof TensorInfo tensorInfo) {
            return tensorInfo.type;
        }
        return OnnxJavaType.INT64;
    }

    private float[] extractVector(Object output, long[] attentionMask) {
        if (output instanceof float[][] pooled) {
            if (pooled.length == 0) {
                throw new BusinessException("本地 Embedding 模型返回空向量");
            }
            return pooled[0].clone();
        }
        if (output instanceof float[][][] tokenEmbeddings) {
            return meanPool(tokenEmbeddings, attentionMask);
        }
        if (output instanceof double[][] pooled) {
            if (pooled.length == 0) {
                throw new BusinessException("本地 Embedding 模型返回空向量");
            }
            return toFloat(pooled[0]);
        }
        if (output instanceof double[][][] tokenEmbeddings) {
            return meanPool(tokenEmbeddings, attentionMask);
        }
        throw new BusinessException("本地 Embedding 模型输出格式不支持: " + output.getClass().getSimpleName());
    }

    private float[] meanPool(float[][][] tokenEmbeddings, long[] attentionMask) {
        if (tokenEmbeddings.length == 0 || tokenEmbeddings[0].length == 0) {
            throw new BusinessException("本地 Embedding 模型返回空向量");
        }
        int tokenCount = tokenEmbeddings[0].length;
        int dimension = tokenEmbeddings[0][0].length;
        float[] pooled = new float[dimension];
        int usedTokens = 0;
        for (int token = 0; token < tokenCount; token++) {
            if (token < attentionMask.length && attentionMask[token] == 0) {
                continue;
            }
            usedTokens++;
            for (int i = 0; i < dimension; i++) {
                pooled[i] += tokenEmbeddings[0][token][i];
            }
        }
        if (usedTokens == 0) {
            usedTokens = tokenCount;
        }
        for (int i = 0; i < dimension; i++) {
            pooled[i] /= usedTokens;
        }
        return pooled;
    }

    private float[] meanPool(double[][][] tokenEmbeddings, long[] attentionMask) {
        if (tokenEmbeddings.length == 0 || tokenEmbeddings[0].length == 0) {
            throw new BusinessException("本地 Embedding 模型返回空向量");
        }
        int tokenCount = tokenEmbeddings[0].length;
        int dimension = tokenEmbeddings[0][0].length;
        float[] pooled = new float[dimension];
        int usedTokens = 0;
        for (int token = 0; token < tokenCount; token++) {
            if (token < attentionMask.length && attentionMask[token] == 0) {
                continue;
            }
            usedTokens++;
            for (int i = 0; i < dimension; i++) {
                pooled[i] += (float) tokenEmbeddings[0][token][i];
            }
        }
        if (usedTokens == 0) {
            usedTokens = tokenCount;
        }
        for (int i = 0; i < dimension; i++) {
            pooled[i] /= usedTokens;
        }
        return pooled;
    }

    private float[] toFloat(double[] source) {
        float[] target = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            target[i] = (float) source[i];
        }
        return target;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
        tokenizer.close();
    }

    private static class MapBackedTensors implements AutoCloseable {
        private final Map<String, OnnxTensor> values;

        private MapBackedTensors(Map<String, OnnxTensor> values) {
            this.values = values;
        }

        @Override
        public void close() {
            for (OnnxTensor tensor : values.values()) {
                tensor.close();
            }
        }
    }
}
