package com.spzx.aichat.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.spzx.aichat.service.IAiChatService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.web.page.TableDataInfo;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
        // 创建一个JSON对象
        JSONObject tool = buildSearchProductTool();

        //存放多个JSON的Array
        JSONArray messages = new JSONArray();
        messages.add(systemMessage());
        messages.add(userMessage(question));

        // 最多允许循环5轮，防止大模型无限重试导致死循环、烧token
        int maxRounds = 5;
        for (int i = 0; i < maxRounds; i++) {

            JSONObject response = callAi(messages, JSONArray.of(tool));
            //DeepSeek（所有 OpenAI 兼容的大模型）返回的 response 长得大约是这个样子：
            /**
             * {
             *   "id": "chatcmpl-123",
             *   "choices": [  // 这是一个 JSON 数组
             *     {
             *       "index": 0,
             *       "message": {   // 这就是你的代码最终要拿到的东西！
             *         "role": "assistant",
             *         "content": "为您推荐以下商品...", // 或者有 "tool_calls" 字段
            *        "tool_calls": [
            *               {
            *                 "id": "call_abc123xyz",          // 👈 唯一的调用ID
            *                 "type": "function",              // 👈 表示这是一个函数工具调用
            *                 "function": {
            *                   "name": "search_product",      // 👈 工具/函数的名字（就是你定义的）
            *                   "arguments": "{\"keyword\": \"红米手机\"}" // 👈 工具需要的实际参数
            *                 }
            *               }
             *           ]
             *       }
             *     }
             *   ]
             * }
             */
            JSONObject message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message");

            // 没有工具调用，说明大模型认为可以给出最终答案了，直接返回
            //比如这些问题：“你好”、“小米这个牌子怎么样”，不需要通过外部查库，大模型可以给出直接答案，那么就没有 tool_calls 字段
            if (!message.containsKey("tool_calls") || message.getJSONArray("tool_calls").isEmpty()) {
                return message.getString("content");
            }

            // 有工具调用，执行它，并把结果重新塞回对话历史，进入下一轮循环
            messages.add(message);  // 助手这条"我要调用xxx"的消息本身要保留

            JSONArray toolCalls = message.getJSONArray("tool_calls");
            for (int j = 0; j < toolCalls.size(); j++) {
                //依次处理每个tool_call的JSON对象
                JSONObject toolCall = toolCalls.getJSONObject(j);
                // 获取tool_call的id
                String toolCallId = toolCall.getString("id");
                // 获取tool_call的function字段
                JSONObject funcCall = toolCall.getJSONObject("function");
                // 获取tool_call的function字段中的arguments字段
                JSONObject args = JSONObject.parseObject(funcCall.getString("arguments"));
                // 获取tool_call的function字段中的arguments字段中的keyword字段
                String keyword = args.getString("keyword");

                // 调用真实商品查询接口
                String productData = queryRealProductData(keyword);

                JSONObject toolResultMsg = new JSONObject();
                toolResultMsg.put("role", "tool");
                toolResultMsg.put("tool_call_id", toolCallId);
                toolResultMsg.put("content", productData);
                messages.add(toolResultMsg);
            }
            // 循环回到for开头，带着新的对话历史再问一次大模型
        }

        return "抱歉，暂时没能帮您找到合适的商品，换个说法再试试？";
    }

    /**
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "search_product",
     *     "description": "...",
     *     "parameters": {
     *       "type": "object",
     *       "properties": {
     *         "keyword": {    // 👈 注意看这里！前面的 "keyword" 是标签
     *           "type": "string",  // 👈 这个花括号里的内容，就是你的 keywordProp 对象
     *           "description": "商品名称关键词..."
     *         }
     *       },
     *       "required": ["keyword"]
     *     }
     *   }
     * }
     * @return
     */
    private JSONObject buildSearchProductTool() {
        JSONObject tool = new JSONObject();
        // 声明这是一个函数工具
        tool.put("type", "function");
        JSONObject function = new JSONObject();
        // 函数工具的名称
        function.put("name", "search_product");
        // 函数工具的描述
        function.put("description", "根据商品关键词查询真实的商品名称和价格信息，关键词只填商品名相关的词，不要包含价格描述");
        JSONObject parameters = new JSONObject();
        // 参数的类型是对象
        parameters.put("type", "object");
        //
        JSONObject properties = new JSONObject();
        //初始化一个描述 keyword（关键词）这个参数属性的空对象
        JSONObject keywordProp = new JSONObject();
        // 描述 keyword（关键词）这个参数属性的类型是字符串
        keywordProp.put("type", "string");
        // 描述 keyword（关键词）这个参数属性的含义
        keywordProp.put("description", "商品名称关键词，比如  '手机'、'红米Note10'，不要包含价格、预算等描述性文字");
        // 将 keyword（关键词）这个参数属性添加到 properties 对象中
        properties.put("keyword", keywordProp);
        // 将 properties 对象添加到 parameters 对象中
        parameters.put("properties", properties);
        // 描述 keyword（关键词）这个参数属性是必填的
        parameters.put("required", JSONArray.of("keyword"));
        //套娃
        function.put("parameters", parameters);
        //套娃
        tool.put("function", function);
        return tool;
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
        // 用你项目中已经引入的 fastjson2，把 LinkedHashMap 安全地转换成 ProductSku 对象
        List<ProductSku> skus = JSON.parseArray(JSON.toJSONString(result.getData().getRows()), ProductSku.class);
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
        //把你在 Nacos 里配置的 model 、 messages（对话历史和系统指令），以及 tools（工具说明书），打包成一个完整的 JSON 包裹。
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", tools);

        // 发送POST请求到大模型API，并获取响应
        String responseStr = aiWebClient.post()
                //OpenAI 和 DeepSeek 大模型标准聊天接口的固定路径
                .uri("/chat/completions")
                // 设置请求头，包括授权信息和内容类型
                .header("Authorization", "Bearer " + apiKey)
                //发给你的内容格式是纯文本的 JSON 结构。
                .header("Content-Type", "application/json")
                //把JSON对象转换成字符串作为请求体
                .bodyValue(body.toJSONString())
                //执行 HTTP 请求
                .retrieve()
                // 捕获异常，给用户一个友好地兜底回复：
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> Mono.error(new ServiceException("AI服务暂时繁忙，请稍后再试")))
                //只接收纯文本格式（String）的内容
                .bodyToMono(String.class)
                .block();  // 简单起见先用同步阻塞方式，后面熟练了可以改成真正的响应式

        return JSONObject.parseObject(responseStr);
    }

    private JSONObject systemMessage() {
        JSONObject msg = new JSONObject();
        //生成一个JSON对象
        msg.put("role", "system");
        //系统设定的内容
        msg.put("content", "你是一个电商平台的智能客服，负责回答用户关于商品价格、库存的问题。" +
                "涉及具体商品信息时，必须调用 search_product 工具查询真实数据，不允许编造价格。" +
                "回复内容必须是纯口语化文字，绝对不能使用Markdown格式（不要用**加粗**、不要用|表格|、不要用-列表符号），" +
                "多个商品之间直接换行分隔即可，例如：\n小米手机10（白色）售价¥2000\n小米手机10（红色）售价¥3999");
        return msg;
    }

    private JSONObject userMessage(String question) {
        JSONObject msg = new JSONObject();
        //生成一个JSON对象
        // 大模型视角下的 JSON 规范：
        /**
         * "role": "system" ➡️ 系统设定。
         * "role": "user" ➡️ 用户输入。
         * "role": "assistant" ➡️ 大模型输出。
         * "role": "tool" ➡️ 工具（后端代码）返回的结果。
         */
        msg.put("role", "user");
        msg.put("content", question);
        // 用户输入的内容
        return msg;
    }
}