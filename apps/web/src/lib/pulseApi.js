const API=(import.meta.env.VITE_API_BASE_URL||'').trim().replace(/\/$/,'');

export function apiConfigurationError(){return new Error('PULSE API origin is not configured for this deployment. Set VITE_API_BASE_URL to the canonical HTTPS API.');}

export async function pulseApi(path,options={}){
  if(!API)throw apiConfigurationError();
  const token=localStorage.getItem('pulse_access_token');
  const headers={...(options.body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{}) ,...(options.headers||{})};
  const response=await fetch(`${API}${path}`,{...options,headers});
  if(response.status===401){localStorage.removeItem('pulse_access_token');localStorage.removeItem('pulse_org_id');throw new Error('Session expired');}
  if(!response.ok)throw new Error(await response.text()||`HTTP ${response.status}`);
  return response.status===204?null:response.json();
}

export function pulseApiConfigured(){return Boolean(API);}
