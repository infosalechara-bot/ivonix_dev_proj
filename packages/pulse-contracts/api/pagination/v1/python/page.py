from typing import Generic,TypeVar
from pydantic import BaseModel,ConfigDict
T=TypeVar('T')
class PulsePage(BaseModel,Generic[T]):
 model_config=ConfigDict(extra='forbid'); items:list[T]; cursor:str|None=None; has_more:bool; total:int|None=None
