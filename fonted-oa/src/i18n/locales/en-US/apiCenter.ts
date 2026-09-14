export default {
 eyebrow:'INTEGRATION · CONTROLLED SANDBOX',title:'API Center',subtitle:'Register fixed upstreams and relative paths, run sandbox checks inside server-side controls, and retain traceable results.',gatewayBadge:'Server allowlisted upstreams only',
 actions:{create:'Register endpoint',execute:'Run check',confirmExecute:'Confirm sandbox check',executeHint:'This will call the fixed sandbox upstream for “{{name}}”. Redirects will not be followed.',confirmStatus:'Confirm endpoint status change'},
 stats:{total:'Endpoints',active:'Active',successful:'Successful calls',failed:'Failed calls'},filters:{keyword:'Search code, name, or upstream',status:'All statuses'},
 columns:{endpoint:'Endpoint',method:'Method',path:'Controlled target',status:'Status',updatedAt:'Updated'},status:{DRAFT:'Draft',ACTIVE:'Active',DISABLED:'Disabled'},outcome:{SUCCESS:'Success',FAILED:'Failed'},
 editor:{create:'Register controlled endpoint',edit:'Edit endpoint',securityTitle:'Security boundary',securityHint:'Only server-configured, sandbox-labelled upstreams can be selected. Domains, auth headers, cookies, tokens, and passwords are never accepted from this page.'},
 fields:{code:'Endpoint code',name:'Endpoint name',upstream:'Sandbox upstream',method:'HTTP method',path:'Relative path',template:'JSON request template',description:'Description'},
 upstream:{available:'Available',unavailable:'Not configured'},detail:{title:'Endpoint details',history:'Latest 50 checks',empty:'No checks yet'},
 messages:{saved:'Endpoint saved',status:'Endpoint status updated',success:'Sandbox check succeeded',failed:'Sandbox check failed: {{code}}'},
 errors:{UPSTREAM_UNAVAILABLE:'Sandbox upstream unavailable',METHOD_NOT_ALLOWED:'Method not allowed',TARGET_REJECTED:'Target is outside the allowed scope',RESPONSE_TOO_LARGE:'Response exceeded the safe size limit',REQUEST_INTERRUPTED:'Request interrupted',UPSTREAM_HTTP_ERROR:'Upstream returned an error status'},
};
