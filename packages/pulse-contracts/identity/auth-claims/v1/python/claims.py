from typing import Literal
from pydantic import BaseModel,ConfigDict,Field
class PulseClaims(BaseModel):
 model_config=ConfigDict(extra='forbid'); sub:str; iat:int=Field(ge=0); exp:int=Field(gt=0); organization_id:str; org_id:str; role:Literal['owner','admin','operator','viewer']; scopes:list[str]; actor_type:Literal['user','service','device','founder']; session_id:str; iss:Literal['pulse']='pulse'; aud:Literal['pulse-api']='pulse-api'
