export default {
 eyebrow:'INTEGRATION · CONTROLLED SANDBOX',title:'接口联调中心',subtitle:'登记固定上游与相对路径，在服务端安全边界内执行沙箱联调并留存可追踪结果。',gatewayBadge:'仅允许服务端白名单上游',
 actions:{create:'登记接口',execute:'执行联调',confirmExecute:'确认执行沙箱联调',executeHint:'即将调用“{{name}}”对应的固定沙箱上游。请求不会跟随重定向。',confirmStatus:'确认变更接口状态'},
 stats:{total:'接口总数',active:'已启用',successful:'成功调用',failed:'失败调用'},filters:{keyword:'搜索编码、名称或上游',status:'全部状态'},
 columns:{endpoint:'接口',method:'方法',path:'受控目标',status:'状态',updatedAt:'更新时间'},status:{DRAFT:'草稿',ACTIVE:'已启用',DISABLED:'已停用'},outcome:{SUCCESS:'成功',FAILED:'失败'},
 editor:{create:'登记受控接口',edit:'编辑接口',securityTitle:'安全边界',securityHint:'只能选择服务端环境变量中登记且标记为沙箱的上游；页面不接收域名、认证头、Cookie、令牌或密码。'},
 fields:{code:'接口编码',name:'接口名称',upstream:'沙箱上游',method:'请求方法',path:'相对路径',template:'JSON 请求模板',description:'接口说明'},
 upstream:{available:'可用',unavailable:'未配置'},detail:{title:'接口详情',history:'最近 50 次联调',empty:'尚无联调记录'},
 messages:{saved:'接口已保存',status:'接口状态已更新',success:'沙箱联调成功',failed:'沙箱联调失败：{{code}}'},
 errors:{UPSTREAM_UNAVAILABLE:'上游沙箱不可用',METHOD_NOT_ALLOWED:'请求方法不受支持',TARGET_REJECTED:'目标地址不在允许范围',RESPONSE_TOO_LARGE:'响应超过安全大小限制',REQUEST_INTERRUPTED:'请求已中断',UPSTREAM_HTTP_ERROR:'上游返回异常状态'},
};
