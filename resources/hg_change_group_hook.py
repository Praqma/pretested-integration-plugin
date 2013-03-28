from mercurial import ui, hg
from mercurial.node import hex
from httplib import HTTPConnection
from urllib import urlencode
import os

def run(ui, repo, **kwargs):
	user_branch = repo[None].branch()
	user_changeset = repo.changelog.tip()
	user_name = repo[None].user()
	user_repository_url = os.getcwd()
	#rev = repo[None].rev()
	#ui.warn(dir(repo[None]) ) 
	ui.warn('branch name: %s \n' %(str(user_branch)))
	ui.warn('changeset: %s \n' %(hex(user_changeset)))
	ui.warn('user: %s \n' %(user_name))
	ui.warn('repo_rul: %s \n' %(user_repository_url))
	#ui.warn('rev: %s \n' %(str(rev)))
	http = HTTPConnection('localhost')
	data = {"user_branch":str(user_branch), "user_changeset":hex(user_changeset),"user_name":user_name,"user_repository_url":"file://"+user_repository_url }
	ui.warn("http://localhost"+urlencode(data)+'\n')
	http.request("GET","http://localhost"+urlencode(data)+'\n')
	ui.warn(str(http.getresponse()))
	return True


