export interface PulseErrorBody{code:string;message:string;message_key:string;details:Record<string,unknown>;request_id:string;docs_url?:string}
export interface PulseErrorResponse{error:PulseErrorBody;error_legacy?:string;fields?:Record<string,string>}
