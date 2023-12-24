"""
除了有component的, 其他都不生成中间type，直接reduce到最终类型。
原子类型硬编码几个type, 先根据type硬编码, 然后根据name有一些特化处理
比如Integer统一编码为HexNumber
"""

import json


def is_optional(schema_list):
    for i in schema_list:
        if i.get("$ref") == "#/components/schemas/Null":
            return True
    else:
        return False


caseclasses = {}
def reduce_schema_to_type(schema, schemas):
    """
    根据schemas返回对应的scala类型
    """
    # print(schema)
    if schema.get("title") in ["blockDifficulty","integer", "position", "blockSize", "blockGasLimit","blockTimeStamp", "blockGasUsed", "timestamp","transactionGas","transactionGasPrice","transactionSigV","transactionSigR","transactionSigS"]:
        return "HexUint"

    if schema.get("title") == "address":
        return "Address"

    if schema.get("title") in ["dataWord", "blockExtraData", "bytes", "blockHash", "position", "keccak", "bloomFilter", "blockLogsBloom","transactionInput"]:
        return "Array[Byte]"
    
    if schema.get("type") == "boolean":
        return "Boolean"

    if 'enum' in schema:
        cases = '\n'.join([f'case {i}' for i in schema['enum']])
        caseclasses[schema['title']] = f"""enum {schema['title']} derives Schema {{
{cases}
        }}"""
        return schema['title']

    if schema.get('title') == "transactionOrTransactionHash":
        return schema['title']

    if schema.get("type") == "object":
        if schema['title'] not in caseclasses:
            items = {}
            for k, v in schema["properties"].items():
                items[k] = reduce_schema_to_type(v, schemas)
            fields = ', '.join([ f'{k}: {v}' for k,v in items.items()])
            casecls = f"case class {schema['title']} ({fields}) derives Schema"
            caseclasses[schema['title']] = casecls
        return schema['title']
    if "$ref" in schema:
        if schema["$ref"].startswith("#/components/schemas"):
            return reduce_schema_to_type(
                schemas[schema["$ref"].split("/")[-1]], schemas
            )
        else:
            raise Exception(f"unknown ref {schema['$ref']}")
    elif "oneOf" in schema:
        if is_optional(schema["oneOf"]):
            for i in schema["oneOf"]:
                if "$ref" not in i or i["$ref"] != "#/components/schemas/Null":
                    return f"Option[{reduce_schema_to_type(i, schemas)}]"
        else:
            # todo 生成enum
            raise Exception(f"not support oneOf {schema}")
    elif schema.get("type") == "array":
        return f'Seq[{reduce_schema_to_type(schema["items"], schemas)}]'

    elif schema.get("type") == "string":
        return "String"
    else:
        raise Exception(f"unknown schema {schema}")


spec = json.load(open("spec.json"))
components = spec["components"]




schemas = components["schemas"]
with open('schemas.scala', 'w') as f:
    f.write('package liewhite.ethers.rpc\n')
    f.write('import liewhite.json.{*, given}\n')
    f.write('import liewhite.ethers.types.*\n')

    for name, schema in schemas.items():
        if schema.get('type') == 'null':
            continue
        reduce_schema_to_type(schema, schemas)
        
    for name, d in caseclasses.items():
        f.write(d + '\n')

