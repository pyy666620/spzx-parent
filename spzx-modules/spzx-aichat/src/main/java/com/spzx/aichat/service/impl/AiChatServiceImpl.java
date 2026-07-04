package com.spzx.aichat.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.spzx.aichat.service.IAiChatService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.web.page.TableDataInfo;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    @Autowired
    private WebClient aiWebClient;

    @Autowired
    private RemoteProductService remoteProductService;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Override
    public String ask(String question) {

        // 1. 定义一个"工具"，告诉大模型：你可以调用这个函数去查真实商品数据
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        JSONObject function = new JSONObject();
        function.put("name", "search_product");
        function.put("description", "根据商品关键词查询真实的商品名称和价格信息");
        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        JSONObject properties = new JSONObject();
        JSONObject keywordProp = new JSONObject();
        keywordProp.put("type", "string");
        keywordProp.put("description", "商品名称关键词，比如'手机'、'红米Note10'");
        properties.put("keyword", keywordProp);
        parameters.put("properties", properties);
        parameters.put("required", JSONArray.of("keyword"));
        function.put("parameters", parameters);
        tool.put("function", function);

        // 2. 组装对话消息列表
        JSONArray messages = new JSONArray();
        messages.add(systemMessage());
        messages.add(userMessage(question));

        // 3. 第一次请求：把问题 + 工具定义都发给大模型，让它自己决定要不要调用工具
        JSONObject firstResponse = callAi(messages, JSONArray.of(tool));
        JSONObject choice = firstResponse.getJSONArray("choices").getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");

        // 4. 判断大模型是否决定调用工具
        if (message.containsKey("tool_calls") && !message.getJSONArray("tool_calls").isEmpty()) {

            JSONObject toolCall = message.getJSONArray("tool_calls").getJSONObject(0);
            String toolCallId = toolCall.getString("id");
            JSONObject funcCall = toolCall.getJSONObject("function");
            JSONObject args = JSONObject.parseObject(funcCall.getString("arguments"));
            String keyword = args.getString("keyword");

            // 5. 真正去查数据库（复用你已有的 skuList 接口，数据100%真实）
            String productData = queryRealProductData(keyword);

            // 6. 把"助手决定调用工具"这条消息、以及"工具返回的真实数据"都补进对话历史
            messages.add(message);  // assistant的tool_calls消息原样放回去
            JSONObject toolResultMsg = new JSONObject();
            toolResultMsg.put("role", "tool");
            toolResultMsg.put("tool_call_id", toolCallId);
            toolResultMsg.put("content", productData);
            messages.add(toolResultMsg);

            // 7. 第二次请求：带着真实数据，让大模型组织成自然语言回复
            JSONObject secondResponse = callAi(messages, JSONArray.of(tool));
            return secondResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        }

        // 没有调用工具，说明是闲聊，直接返回大模型自己的回复
        return message.getString("content");
    }

    /**
     * 真正查数据库，保证价格/库存这些关键数字是真实的，不是大模型编的
     */
    private String queryRealProductData(String keyword) {
        SkuQuery skuQuery = new SkuQuery();
        skuQuery.setKeyword(keyword);
        R<TableDataInfo> result = remoteProductService.skuList(1, 5, skuQuery, SecurityConstants.INNER);

        if (R.FAIL == result.getCode()) {
            return "查询失败";
        }
        List<ProductSku> skus = (List<ProductSku>) result.getData().getRows();
        if (skus == null || skus.isEmpty()) {
            return "没有找到相关商品";
        }
        JSONArray arr = new JSONArray();
        for (ProductSku sku : skus) {
            JSONObject obj = new JSONObject();
            obj.put("skuName", sku.getSkuName());
            obj.put("price", sku.getSalePrice());
            arr.add(obj);
        }
        return arr.toJSONString();
    }

    private JSONObject callAi(JSONArray messages, JSONArray tools) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", tools);

        String responseStr = aiWebClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body.toJSONString())
                .retrieve()
                .bodyToMono(String.class)
                .block();  // 简单起见先用同步阻塞方式，后面熟练了可以改成真正的响应式

        return JSONObject.parseObject(responseStr);
    }

    private JSONObject systemMessage() {
        JSONObject msg = new JSONObject();
        msg.put("role", "system");
        msg.put("content", "你是一个电商平台的智能客服，负责回答用户关于商品价格、库存的问题。" +
                "涉及具体商品信息时，必须调用 search_product 工具查询真实数据，不允许编造价格。");
        return msg;
    }

    private JSONObject userMessage(String question) {
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", question);
        return msg;
    }
}