import {expect,it,vi} from 'vitest';
import {reportQuery,reportRangeError,downloadReport} from './reports';
const filters={from:'2030-01-01',to:'2030-03-31',professionalId:'',serviceId:''};
it('aceita exatamente 90 dias e recusa datas impossíveis, invertidas ou vazias',()=>{
 expect(reportRangeError(filters)).toBe('');for(const f of [{...filters,to:'2030-04-01'},{...filters,from:''},{...filters,from:'2030-02-30'},{...filters,to:'2029-12-31'}])expect(reportRangeError(f)).not.toBe('');
});
it('monta parâmetros codificados sem introduzir outro filtro',()=>{expect(reportQuery({...filters,professionalId:'x&serviceId=other'})).toBe('from=2030-01-01&to=2030-03-31&professionalId=x%26serviceId%3Dother');});
it('download usa arquivo local temporário e libera a URL depois',()=>{
 vi.useFakeTimers();const create=vi.fn(()=> 'blob:report'),revoke=vi.fn();Object.defineProperty(URL,'createObjectURL',{value:create,configurable:true});Object.defineProperty(URL,'revokeObjectURL',{value:revoke,configurable:true});
 const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(function(this:HTMLAnchorElement){expect(this.download).toBe('AgendaClara-relatorio-2030-01-01-a-2030-03-31.csv');expect(this.href).toBe('blob:report');});
 try{downloadReport(new Blob(['test']),filters);expect(click).toHaveBeenCalledOnce();expect(document.querySelector('a')).toBeNull();vi.advanceTimersByTime(1500);expect(revoke).toHaveBeenCalledWith('blob:report');}finally{click.mockRestore();vi.useRealTimers();}
});