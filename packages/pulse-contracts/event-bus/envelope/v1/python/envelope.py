from typing import Generic,Optional,TypeVar
from pydantic import BaseModel,ConfigDict,Field
T=TypeVar('T')
class PulseEvent(BaseModel,Generic[T]):
 model_config=ConfigDict(extra='forbid')
 event_id:str=Field(pattern=r'^[0-9A-HJKMNP-TV-Z]{26}$')
 event_type:str
 event_version:int=Field(ge=1)
 envelope_version:int=1
 occurred_at:str=Field(pattern=r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$')
 producer:str; aggregate_type:str; aggregate_id:str; org_id:Optional[str]=None; correlation_id:str; causation_id:Optional[str]=None; payload:T; signature:str
