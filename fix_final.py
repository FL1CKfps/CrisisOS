import sys

def process(content):
    content = content.replace('log("Connection initiated with \\")', 'log(f"Connection initiated with {endpointId}")')
    content = content.replace('log("Connection rejected: \\")', 'log(f"Connection rejected: {endpointId}")')
    content = content.replace('log("Connection request failed: \\")', 'log(f"Connection request failed: {endpointId}")')
    content = content.replace('log("Max connections reached, ignoring \\")', 'log(f"Max connections reached, ignoring {endpointId}")')
    content = content.replace('Log.e("CrisisOS_Mesh", "Advertising failed, retrying in 5s: \\")', 'Log.e("CrisisOS_Mesh", f"Advertising failed, retrying in 5s: {e.message}")')
    content = content.replace('Log.e("CrisisOS_Mesh", "Discovery failed, retrying in 5s: \\")', 'Log.e("CrisisOS_Mesh", f"Discovery failed, retrying in 5s: {e.message}")')
    return content

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = process(text)
text = text.replace('f"Connection initiated with {endpointId}"', '"Connection initiated with "')
text = text.replace('f"Connection rejected: {endpointId}"', '"Connection rejected: "')
text = text.replace('f"Connection request failed: {endpointId}"', '"Connection request failed: "')
text = text.replace('f"Max connections reached, ignoring {endpointId}"', '"Max connections reached, ignoring "')
text = text.replace('f"Advertising failed, retrying in 5s: {e.message}"', '"Advertising failed, retrying in 5s: "')
text = text.replace('f"Discovery failed, retrying in 5s: {e.message}"', '"Discovery failed, retrying in 5s: "')

with open('app/src/main/java/com/elv8/crisisos/data/mesh/MeshConnectionManager.kt', 'w', encoding='utf-8') as f:
    f.write(text)

