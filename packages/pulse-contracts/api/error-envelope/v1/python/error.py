from typing import Any
from pydantic import BaseModel,ConfigDict,Field
class PulseError(BaseModel):
 model_config=ConfigDict(extra='forbid'); code:str; message:str; message_key:str; details:dict[str,Any]={}; request_id:str=Field(pattern=r'^req_[a-z0-9]{21}$'); docs_url:str|None=None
class PulseErrorResponse(BaseModel):
 model_config=ConfigDict(extra='forbid'); error:PulseError; error_legacy:str|None=None; fields:dict[str,str]|None=None
