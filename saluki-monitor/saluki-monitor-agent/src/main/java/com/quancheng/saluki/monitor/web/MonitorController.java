package com.quancheng.saluki.monitor.web;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Maps;
import com.quancheng.saluki.core.grpc.GRPCEngine;
import com.quancheng.saluki.core.utils.NetUtils;
import com.quancheng.saluki.monitor.SalukiInvoke;
import com.quancheng.saluki.monitor.SalukiInvokeStatistics;
import com.quancheng.saluki.monitor.mapper.SalukiInvokeMapper;
import com.quancheng.saluki.monitor.util.DateUtil;
import com.quancheng.saluki.monitor.util.SpringBeanUtils;

@RestController
@RequestMapping("/salukiMonitor")
public class MonitorController {

    private SimpleDateFormat         formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

    private final SalukiInvokeMapper mapper    = SpringBeanUtils.getBean(SalukiInvokeMapper.class);

    @RequestMapping(value = "/system", method = RequestMethod.GET)
    public Map<String, Object> system() {
        List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[] { "Saluki_Version", getSalukiVersion() });
        String address = NetUtils.getLocalHost();
        rows.add(new String[] { "Host", NetUtils.getHostName(address) + "/" + address });
        rows.add(new String[] { "OS", System.getProperty("os.name") + " " + System.getProperty("os.version") });
        rows.add(new String[] { "JVM",
                                System.getProperty("java.runtime.name") + " "
                                       + System.getProperty("java.runtime.version") + ",<br/>"
                                       + System.getProperty("java.vm.name") + " "
                                       + System.getProperty("java.vm.version") + " "
                                       + System.getProperty("java.vm.info", "") });

        rows.add(new String[] { "CPU", System.getProperty("os.arch", "") + ", "
                                       + String.valueOf(Runtime.getRuntime().availableProcessors()) + " cores" });

        rows.add(new String[] { "Locale", Locale.getDefault().toString() + "/" + System.getProperty("file.encoding") });

        rows.add(new String[] { "Uptime", DateUtil.formatUptime(ManagementFactory.getRuntimeMXBean().getUptime()) });

        rows.add(new String[] { "Time", formatter.format(new Date()) });

        Map<String, Object> model = Maps.newHashMap();
        model.put("rows", rows);
        return model;
    }

    private String getSalukiVersion() {
        Class<?> clazz = GRPCEngine.class;
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        if (protectionDomain == null || protectionDomain.getCodeSource() == null) {
            return null;
        }
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }
        String path = codeSource.getLocation().toExternalForm();
        if (path.endsWith(".jar") && path.contains("/")) {
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return path;
    }

    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public List<SalukiInvoke> data(@RequestParam(value = "service", required = true) String service,
                                   @RequestParam(value = "type", required = true) String type) {
        Map<String, String> queryType = Maps.newHashMap();
        queryType.put("service", service);
        queryType.put("type", type);
        return mapper.queryData(queryType);
    }

    @RequestMapping(value = "/statistics", method = RequestMethod.GET)
    public List<SalukiInvokeStatistics> statistics(@RequestParam(value = "service", required = true) String service,
                                                   @RequestParam(value = "type", required = true) String type) {
        Map<String, String> queryType = Maps.newHashMap();
        queryType.put("service", service);
        queryType.put("type", type);
        List<SalukiInvokeStatistics> statistics = mapper.queryStatistics(queryType);
        for (Iterator<SalukiInvokeStatistics> it = statistics.iterator(); it.hasNext();) {
            SalukiInvokeStatistics st = it.next();
            Double sumConsurrent = st.getSumconcurrent();
            Double sumElapsed = st.getSumelapsed();
            Double sumSuccess = st.getSumsuccess();
            Double sumFailure = st.getSumfailure();
            Double sumInput = st.getSuminput();
            Double totalCount = sumFailure + sumSuccess;
            BigDecimal averageElapsed = BigDecimal.valueOf(sumElapsed).divide(BigDecimal.valueOf(totalCount), 2,
                                                                              BigDecimal.ROUND_HALF_DOWN);
            st.setElapsed(averageElapsed.doubleValue());
            BigDecimal averageInput = BigDecimal.valueOf(sumInput).divide(BigDecimal.valueOf(totalCount), 2,
                                                                          BigDecimal.ROUND_HALF_DOWN);
            // TPS=并发数/平均响应时间
            BigDecimal tps = new BigDecimal(sumConsurrent);
            if (!BigDecimal.ZERO.equals(sumConsurrent) && !BigDecimal.ZERO.equals(averageElapsed)) {
                tps = tps.divide(averageElapsed, 2, BigDecimal.ROUND_HALF_DOWN);
                tps = tps.multiply(BigDecimal.valueOf(1000));
                st.setTps(tps.doubleValue());
            }
            // kbps=tps*平均每次传输的数据量
            BigDecimal kbps = new BigDecimal(st.getTps());
            if (!BigDecimal.ZERO.equals(averageElapsed) && !BigDecimal.ZERO.equals(averageInput)) {
                kbps = kbps.multiply(averageInput.divide(BigDecimal.valueOf(1024), 2, BigDecimal.ROUND_HALF_DOWN));
                st.setKbps(kbps.doubleValue());
            }

        }
        return statistics;
    }

    @RequestMapping(value = "/clean", method = RequestMethod.GET)
    public void cleanData() {
        mapper.truncateTable();
    }

}
