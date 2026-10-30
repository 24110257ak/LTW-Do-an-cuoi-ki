const {test} = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/product-purchase.js');
const pause = () => new Promise(resolve => setTimeout(resolve, 15));
function page(count=1, signedIn=true) {
    const ids=['addToCartForm','inputQuantity','btnQtyMinus','btnQtyPlus','btnAddToCart','btnBuyNow','stockLimitHint'];
    const nodes=Object.fromEntries(ids.map(id=>[id,{value:'1',disabled:false,textContent:'',handlers:{},addEventListener(name,fn){this.handlers[name]=fn;}}]));
    const radio={value:'1'};
    nodes.addToCartForm.dataset={maxQuantity:'10',inCart:String(count),signedIn:String(signedIn),limitUrl:'/cart/limits'};
    nodes.addToCartForm.querySelector=()=>radio;
    return {nodes,radio,doc:{getElementById:id=>nodes[id]}};
}

test('cart already has one: show nine remaining and reject adding ten while allowing buy-now',async()=>{
    const p=page();init(p.doc,async()=>({ok:true,json:async()=>({inCartQuantity:1,maxAdditionalQuantity:9})}));await pause();
    assert.match(p.nodes.stockLimitHint.textContent,/đã có 1.*tối đa 9/);
    for(let i=0;i<9;i++)p.nodes.btnQtyPlus.handlers.click();
    assert.equal(p.nodes.inputQuantity.value,'10');assert.equal(p.nodes.btnAddToCart.disabled,true);assert.equal(p.nodes.btnBuyNow.disabled,false);
    let prevented=false;p.nodes.addToCartForm.handlers.submit({submitter:p.nodes.btnAddToCart,preventDefault(){prevented=true;}});assert.equal(prevented,true);
    prevented=false;p.nodes.addToCartForm.handlers.submit({submitter:p.nodes.btnBuyNow,preventDefault(){prevented=true;}});assert.equal(prevented,false);
    p.nodes.btnQtyMinus.handlers.click();assert.equal(p.nodes.btnAddToCart.disabled,false);
});
test('full cart disables add, but a separate buy-now remains available',async()=>{
    const p=page(10);init(p.doc,async()=>({ok:true,json:async()=>({inCartQuantity:10,maxAdditionalQuantity:0})}));await pause();
    assert.match(p.nodes.stockLimitHint.textContent,/tối đa 0/);assert.equal(p.nodes.btnAddToCart.disabled,true);assert.equal(p.nodes.btnBuyNow.disabled,false);
});
test('guest does not request authenticated cart limits',()=>{
    const p=page(0,false);let calls=0;init(p.doc,async()=>{calls++;});assert.equal(calls,0);assert.equal(p.nodes.btnBuyNow.disabled,false);
});
test('stale limit response cannot overwrite the newer count',async()=>{
    const p=page();const pending=[];init(p.doc,()=>new Promise(resolve=>pending.push(resolve)));
    p.radio.value='2';p.nodes.addToCartForm.handlers.change({target:{name:'variantId'}});
    pending[1]({ok:true,json:async()=>({inCartQuantity:10})});await pause();
    pending[0]({ok:true,json:async()=>({inCartQuantity:0})});await pause();
    assert.match(p.nodes.stockLimitHint.textContent,/đã có 10/);assert.equal(p.nodes.btnAddToCart.disabled,true);
});
test('failed limit fetch prevents stale cart additions without blocking independent purchase',async()=>{
    const p=page();init(p.doc,async()=>({ok:false}));await pause();
    assert.equal(p.nodes.btnAddToCart.disabled,true);assert.equal(p.nodes.btnBuyNow.disabled,false);assert.match(p.nodes.stockLimitHint.textContent,/tải lại/);
});
