package com.java2nb.novel.controller;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.AlipayProperties;
import com.java2nb.novel.core.config.PayPalProperties;
import com.java2nb.novel.service.OrderService;
import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author 11797
 */
@Controller
@RequestMapping("pay")
@RequiredArgsConstructor
@Slf4j
public class PayController extends BaseController {


    private final AlipayProperties alipayConfig;

    private final OrderService orderService;

    private final PayPalProperties payPalProperties;

    private static final Logger LOGGER = LoggerFactory.getLogger(PayController.class);

    /**
     * 支付宝支付
     */
    @SneakyThrows
    @PostMapping("aliPay")
    public void aliPay(Integer payAmount,HttpServletRequest request,HttpServletResponse httpResponse) {

        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            //未登录，跳转到登陆页面
            httpResponse.sendRedirect("/user/login.html?originUrl=/pay/aliPay?payAmount="+payAmount);
        }else {
            //创建充值订单
            Long outTradeNo = orderService.createPayOrder((byte)1,payAmount,userDetails.getId());

            //获得初始化的AlipayClient
            AlipayClient alipayClient = new DefaultAlipayClient(alipayConfig.getGatewayUrl(), alipayConfig.getAppId(), alipayConfig.getMerchantPrivateKey(), "json", alipayConfig.getCharset(), alipayConfig.getPublicKey(), alipayConfig.getSignType());
            //创建API对应的request
            AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
            alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());
            //在公共参数中设置回跳和通知地址
            alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());
            //填充业务参数
            alipayRequest.setBizContent("{" +
                    "    \"out_trade_no\":\"" + outTradeNo + "\"," +
                    "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                    "    \"total_amount\":" + payAmount + "," +
                    "    \"subject\":\"小说精品屋-plus\"" +
                    "  }");
            //调用SDK生成表单
            String form = alipayClient.pageExecute(alipayRequest).getBody();

            httpResponse.setContentType("text/html;charset=utf-8");
            //直接将完整的表单html输出到页面
            httpResponse.getWriter().write(form);
            httpResponse.getWriter().flush();
            httpResponse.getWriter().close();
        }




    }
    /**
     * papal pay
     */
    @SneakyThrows
    @PostMapping("paypal")
    public void paypalPay(Integer payAmount,HttpServletRequest req,HttpServletResponse resp) {
        UserDetails userDetails = getUserDetails(req);
        if (userDetails == null) {
            //redirect to login page
            resp.sendRedirect("/user/login.html?originUrl=/pay/aliPay?payAmount=" + payAmount);
            return;
        }
        //create paypal order 3 is paypal
        Long outTradeNo = orderService.createPayOrder((byte)3,payAmount,userDetails.getId());
        //create paypal payments
        APIContext apiContext = new APIContext(payPalProperties.getClientId(), payPalProperties.getClientSecret(), payPalProperties.getMode());
        Details details = new Details();
        details.setSubtotal(String.valueOf(payAmount));
        //TODO set tax
        //details.setTax();

        Amount amount = new Amount();
        amount.setCurrency("USD");
        amount.setTotal(String.valueOf(payAmount));
        amount.setDetails(details);

        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setDescription("payment for novel credits");

        // ### Items
        Item item = new Item();
        item.setName("Novel Credits").setQuantity("1").setCurrency("USD").setPrice(String.valueOf(payAmount));
        ItemList itemList = new ItemList();
        List<Item> items = new ArrayList<Item>();
        items.add(item);
        itemList.setItems(items);
        transaction.setItemList(itemList);

        List<Transaction> transactions = new ArrayList<Transaction>();
        transactions.add(transaction);
        // ###Payer
        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);

        // ###Redirect URLs
        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl(req.getScheme() + "://"
                + req.getServerName() + ":" + req.getServerPort()
                + req.getContextPath() + "/pay/paypal/notify?uid=" + outTradeNo);
        redirectUrls.setReturnUrl(req.getScheme() + "://"
                + req.getServerName() + ":" + req.getServerPort()
                + req.getContextPath() + "/pay/paypal/notify?uid=" + outTradeNo);
        payment.setRedirectUrls(redirectUrls);

        //not try to create payment
        Payment createdPayment = null;
        try {
            createdPayment = payment.create(apiContext);
            LOGGER.info("Created payment with id = "
                    + createdPayment.getId() + " and status = "
                    + createdPayment.getState());
            // ###Payment Approval Url
            Iterator<Links> links = createdPayment.getLinks().iterator();
            while (links.hasNext()) {
                Links link = links.next();
                if (link.getRel().equalsIgnoreCase("approval_url")) {
                    req.setAttribute("redirectURL", link.getHref());
                    //redirect
                    resp.sendRedirect(link.getHref());
                }
            }

        } catch (PayPalRESTException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
            resp.getWriter().print("error create payment, error message " + e.getMessage());
        }
    }
    @SneakyThrows
    @RequestMapping("paypal/notify")
    public void paypalNotify(HttpServletRequest req, HttpServletResponse resp) {
        //paymentid
        String paymentId = req.getParameter("paymentId");
        String outTradeNo = req.getParameter("uid");
        //get trade status
        String payerId = req.getParameter("PayerID");
        Payment payment = new Payment();
        if (paymentId != null) {
            payment.setId(paymentId);
        }

        PaymentExecution paymentExecution = new PaymentExecution();
        paymentExecution.setPayerId(payerId);
        Payment createdPayment = null;
        APIContext apiContext = new APIContext(payPalProperties.getClientId(), payPalProperties.getClientSecret(), payPalProperties.getMode());
        try {
            createdPayment = payment.execute(apiContext, paymentExecution);
        } catch (PayPalRESTException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
            return;
        }
        //get status
        String tradeStatus = createdPayment.getPayer().getStatus();
        orderService.updatePayOrder(Long.parseLong(outTradeNo), paymentId, tradeStatus);
        LOGGER.info("success get pay return");
        resp.sendRedirect("/pay/index.html");

    }

    /**
     * 支付宝支付通知
     * */
    @SneakyThrows
    @RequestMapping("aliPay/notify")
    public void aliPayNotify(HttpServletRequest request,HttpServletResponse httpResponse){


        PrintWriter out = httpResponse.getWriter();

        //获取支付宝POST过来反馈信息
        Map<String,String> params = new HashMap<>();
        Map<String,String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        //调用SDK验证签名
        boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getPublicKey(), alipayConfig.getCharset(), alipayConfig.getSignType());

        //——请在这里编写您的程序（以下代码仅作参考）——

	/* 实际验证过程建议商户务必添加以下校验：
	1、需要验证该通知数据中的out_trade_no是否为商户系统中创建的订单号，
	2、判断total_amount是否确实为该订单的实际金额（即商户订单创建时的金额），
	3、校验通知中的seller_id（或者seller_email) 是否为out_trade_no这笔单据的对应的操作方（有的时候，一个商户可能有多个seller_id/seller_email）
	4、验证app_id是否为该商户本身。
	*/
        if(signVerified) {
            //验证成功
            //商户订单号
            String outTradeNo = new String(request.getParameter("out_trade_no").getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

            //支付宝交易号
            String tradeNo = new String(request.getParameter("trade_no").getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

            //交易状态
            String tradeStatus = new String(request.getParameter("trade_status").getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

            //更新订单状态
            orderService.updatePayOrder(Long.parseLong(outTradeNo), tradeNo, tradeStatus);


            out.println("success");

        }else {//验证失败
            out.println("fail");

            //调试用，写文本函数记录程序运行情况是否正常
            //String sWord = AlipaySignature.getSignCheckContentV1(params);
            //AlipayConfig.logResult(sWord);
        }



    }






}
