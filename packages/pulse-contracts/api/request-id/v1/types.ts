export const REQUEST_ID_PATTERN=/^req_[a-z0-9]{21}$/; export function isRequestId(v:string):boolean{return REQUEST_ID_PATTERN.test(v)}
