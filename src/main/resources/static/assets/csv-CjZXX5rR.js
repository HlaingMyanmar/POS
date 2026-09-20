const l=/^[\p{Cc}\p{Cf}\p{Zs}]*[=+\-@]/u,u=t=>typeof t!="string"||!l.test(t)?t:`'${t}`,i=(t,e={})=>{const s=u(t),n=s==null?"":String(s);return e.alwaysQuote||/[",\r\n]/.test(n)?`"${n.replace(/"/g,'""')}"`:n},p=(t,e={})=>{const{alwaysQuote:s=!1,bom:n=!1,lineEnding:o=`
`}=e,r=t.map(c=>c.map(a=>i(a,{alwaysQuote:s})).join(",")).join(o);return`${n?"\uFEFF":""}${r}`};export{p as t};
