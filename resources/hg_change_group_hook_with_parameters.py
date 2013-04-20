from mercurial import ui, hg
from mercurial.node import hex
from httplib import HTTPConnection
from urllib import urlencode
import os

jenkins_port = '8080'
hg_server_adress = '192.168.19.105'

def run(ui, repo, **kwargs):
	user_branch = repo['tip'].branch()
	user_changeset = repo.changelog.tip()
	user_name = repo['tip'].user()
	user_repository_url = os.getcwd()
	#rev = repo[None].rev()
	#ui.warn('branch name: %s \n' %(str(user_branch)))
	#ui.warn('changeset: %s \n' %(hex(user_changeset)))
	#ui.warn('user: %s \n' %(user_name))
	#ui.warn('repo_url: %s \n' %(user_repository_url))
	#ui.warn('rev: %s \n' %(str(rev)))
	http = HTTPConnection(hg_server_adress+':'+jenkins_port)
	data = {"user_branch":str(user_branch), "user_changeset":hex(user_changeset),"user_name":user_name,"user_repository_url":"file://"+user_repository_url }
	#ui.warn("http://192.168.19.105:8080/job/Github-test%200.01/buildWithParameters?"+urlencode(data)+'\n')
	http.request("GET",hg_server_adress+':'+jenkins_port+"/job/Github-test%200.01/buildWithParameters?"+urlencode(data)+'\n')
	ui.warn(str(http.getresponse())+'\n')
	return False


