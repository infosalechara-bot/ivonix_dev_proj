import re
REQUEST_ID_PATTERN=re.compile(r'^req_[a-z0-9]{21}$')
def is_request_id(value:str)->bool:return bool(REQUEST_ID_PATTERN.fullmatch(value or ''))
