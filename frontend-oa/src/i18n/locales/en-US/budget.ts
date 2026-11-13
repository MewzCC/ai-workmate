export default {
  eyebrow:'FINANCE · BUDGET CONTROL',title:'Budget Center',subtitle:'Plan annual budgets and track reservations, spending, releases, and usage alerts in real time.',
  actions:{create:'New budget',confirmStatus:'Confirm budget status change'},
  stats:{totalPlans:'Budget plans',activePlans:'Active',warningPlans:'Usage alerts',totalAmount:'Active budget',occupiedAmount:'Reserved',availableAmount:'Available'},
  filters:{keyword:'Search code, name, or owner',status:'All statuses',year:'Fiscal year'},
  columns:{budget:'Budget',owner:'Owner',total:'Total',execution:'Execution',available:'Available',status:'Status'},
  status:{DRAFT:'Draft',ACTIVE:'Active',CLOSED:'Closed',CANCELLED:'Cancelled'}, alert:{NORMAL:'Normal',WARNING:'Warning',FULL:'Fully used'},
  usage:'Reserved {{occupied}} · Spent {{spent}}',
  editor:{create:'Create budget',edit:'Edit budget'}, detail:{title:'Budget details',timeline:'Transactions'},
  fields:{code:'Budget code',year:'Fiscal year',name:'Budget name',owner:'Owner',amount:'Total amount',currency:'Currency',threshold:'Alert threshold',summary:'Summary',operationAmount:'Amount',reference:'Reference',note:'Note'},
  operation:{OCCUPY:'Reserve',RELEASE:'Release',SPEND:'Record spend'},
  transaction:{CREATED:'Budget created',UPDATED:'Budget updated',STATUS_ACTIVE:'Budget activated',STATUS_CLOSED:'Budget closed',STATUS_CANCELLED:'Budget cancelled',OCCUPY:'Amount reserved',RELEASE:'Amount released',SPEND:'Spend recorded'},
  messages:{saved:'Budget saved',status:'Budget status updated',operation:'Budget transaction recorded'},
};
